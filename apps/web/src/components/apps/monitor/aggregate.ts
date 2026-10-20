import type { HistoryPoint } from "@/src/components/apps/monitor/format";
import type { ServerInfo } from "@/src/lib/api/server";

/**
 * Cross-server totals for the System Monitor.
 *
 * CPU is capacity-weighted: sum(logical CPUs × utilization) / sum(logical CPUs).
 * A 70% reading on 8 CPUs counts for more than 70% on 2 CPUs.
 *
 * Memory uses bytes, not the average of percentages. Used memory is the
 * collector's total − available figure, which treats reclaimable cache as
 * available. The same definition is used on every server.
 *
 * Disk sums each server's single root-filesystem used and total byte fields.
 * Mount lists are not added, so bind mounts and extra filesystems are not
 * counted twice. A server is skipped when used bytes exceed total bytes.
 *
 * Network sums receive and transmit bytes per second. Those rates are already
 * deltas from the collector. Missing rates are left out; a reported 0 is kept.
 * Throughput is never placed on a 0–100 utilization scale.
 */
export type MetricSample = {
  cpuPercent?: number;
  logicalCpus?: number;
  memoryUsedBytes?: number;
  memoryTotalBytes?: number;
  diskUsedBytes?: number;
  diskTotalBytes?: number;
  netRxBps?: number;
  netTxBps?: number;
};

export type AggregateValue = {
  /** Null when no server contributed a valid reading. Missing data is not zero. */
  percent: number | null;
  used: number;
  total: number;
  rxBps: number;
  txBps: number;
  reporting: number;
};

export function sampleFromInfo(info: ServerInfo | undefined): MetricSample | null {
  if (!info) return null;
  return {
    cpuPercent: finite(info.cpuUsage),
    logicalCpus: positive(info.cpuCores) ?? positive(info.cores?.length),
    memoryUsedBytes: finite(info.memoryUsedBytes),
    memoryTotalBytes: positive(info.memoryTotalBytes),
    diskUsedBytes: finite(info.diskUsedBytes),
    diskTotalBytes: positive(info.diskTotalBytes),
    netRxBps: finite(info.netRxBps),
    netTxBps: finite(info.netTxBps),
  };
}

export function aggregateCpu(samples: MetricSample[]): AggregateValue {
  let used = 0;
  let total = 0;
  let reporting = 0;
  for (const sample of samples) {
    if (sample.cpuPercent == null || sample.logicalCpus == null) continue;
    if (sample.cpuPercent < 0 || sample.cpuPercent > 100) continue;
    used += (sample.cpuPercent / 100) * sample.logicalCpus;
    total += sample.logicalCpus;
    reporting += 1;
  }
  return {
    percent: total > 0 ? (used / total) * 100 : null,
    used,
    total,
    rxBps: 0,
    txBps: 0,
    reporting,
  };
}

export function aggregateMemory(samples: MetricSample[]): AggregateValue {
  return aggregateBytes(
    samples,
    (sample) => sample.memoryUsedBytes,
    (sample) => sample.memoryTotalBytes,
  );
}

export function aggregateDisk(samples: MetricSample[]): AggregateValue {
  return aggregateBytes(
    samples,
    (sample) => sample.diskUsedBytes,
    (sample) => sample.diskTotalBytes,
  );
}

export function aggregateNetwork(samples: MetricSample[]): AggregateValue {
  let rx = 0;
  let tx = 0;
  let reporting = 0;
  for (const sample of samples) {
    if (sample.netRxBps == null || sample.netTxBps == null) continue;
    if (sample.netRxBps < 0 || sample.netTxBps < 0) continue;
    rx += sample.netRxBps;
    tx += sample.netTxBps;
    reporting += 1;
  }
  return { percent: null, used: 0, total: 0, rxBps: rx, txBps: tx, reporting };
}

export type HistorySeries = {
  points: HistoryPoint[];
  /** Latest logical CPU count, used when an older sample did not store capacity. */
  cpuCount?: number;
  memoryTotalBytes?: number;
  diskUsedBytes?: number;
  diskTotalBytes?: number;
};

/**
 * Aligns each server to fixed buckets and aggregates only the samples that
 * landed in that bucket. A server with no sample in the bucket is omitted,
 * not treated as zero. Buckets with no valid CPU, memory, and disk reading
 * are dropped so the chart does not draw a fabricated zero.
 */
export function aggregateHistory(
  series: HistorySeries[],
  rangeMs: number,
  now = Date.now(),
): HistoryPoint[] {
  const bucket = Math.max(2_000, Math.round(rangeMs / 60));
  const start = now - rangeMs;
  const out: HistoryPoint[] = [];
  for (let end = start + bucket; end <= now + bucket; end += bucket) {
    const samples: MetricSample[] = [];
    for (const item of series) {
      const point = latestInBucket(item.points, end - bucket, end);
      if (!point) continue;
      samples.push(sampleFromPoint(point, item));
    }
    if (samples.length === 0) continue;
    const cpu = aggregateCpu(samples);
    const memory = aggregateMemory(samples);
    const disk = aggregateDisk(samples);
    const network = aggregateNetwork(samples);
    if (cpu.percent == null || memory.percent == null || disk.percent == null) continue;
    out.push({
      t: end,
      cpu: cpu.percent,
      cpuUser: 0,
      cpuSystem: 0,
      cpuIowait: 0,
      memory: memory.percent,
      memUsedBytes: memory.used,
      memCachedBytes: 0,
      memBuffersBytes: 0,
      memAvailableBytes: Math.max(0, memory.total - memory.used),
      memTotalBytes: memory.total || undefined,
      disk: disk.percent ?? 0,
      diskReadBps: 0,
      diskWriteBps: 0,
      diskUsedBytes: disk.used || undefined,
      diskTotalBytes: disk.total || undefined,
      netRx: network.reporting > 0 ? network.rxBps : 0,
      netTx: network.reporting > 0 ? network.txBps : 0,
      svcRunning: 0,
      svcStopped: 0,
      svcFailed: 0,
      svcOther: 0,
      cpuCount: cpu.total || undefined,
    });
  }
  return out;
}

/**
 * One aggregate reading per stored sample, matching the density of a
 * single-server sparkline. Coarse time buckets are not used here.
 */
export function sparkHistory(series: HistorySeries[]): HistoryPoint[] {
  const count = series.reduce((max, item) => Math.max(max, item.points.length), 0);
  if (count === 0) return [];
  const out: HistoryPoint[] = [];
  for (let index = 0; index < count; index += 1) {
    const samples: MetricSample[] = [];
    let t = 0;
    for (const item of series) {
      if (item.points.length === 0) continue;
      const at = Math.round((index / Math.max(1, count - 1)) * (item.points.length - 1));
      const point = item.points[at];
      if (!point) continue;
      t = point.t || t;
      samples.push(sampleFromPoint(point, item));
    }
    if (samples.length === 0) continue;
    const cpu = aggregateCpu(samples);
    const memory = aggregateMemory(samples);
    const disk = aggregateDisk(samples);
    const network = aggregateNetwork(samples);
    if (
      cpu.percent == null &&
      memory.percent == null &&
      disk.percent == null &&
      network.reporting === 0
    ) {
      continue;
    }
    out.push({
      t,
      cpu: cpu.percent ?? 0,
      cpuUser: 0,
      cpuSystem: 0,
      cpuIowait: 0,
      memory: memory.percent ?? 0,
      memUsedBytes: memory.used,
      memCachedBytes: 0,
      memBuffersBytes: 0,
      memAvailableBytes: Math.max(0, memory.total - memory.used),
      memTotalBytes: memory.total || undefined,
      disk: disk.percent ?? 0,
      diskReadBps: 0,
      diskWriteBps: 0,
      diskUsedBytes: disk.used || undefined,
      diskTotalBytes: disk.total || undefined,
      netRx: network.reporting > 0 ? network.rxBps : 0,
      netTx: network.reporting > 0 ? network.txBps : 0,
      svcRunning: 0,
      svcStopped: 0,
      svcFailed: 0,
      svcOther: 0,
      cpuCount: cpu.total || undefined,
    });
  }
  return out;
}

function aggregateBytes(
  samples: MetricSample[],
  usedOf: (sample: MetricSample) => number | undefined,
  totalOf: (sample: MetricSample) => number | undefined,
): AggregateValue {
  let used = 0;
  let total = 0;
  let reporting = 0;
  for (const sample of samples) {
    const sampleUsed = usedOf(sample);
    const sampleTotal = totalOf(sample);
    if (sampleUsed == null || sampleTotal == null) continue;
    if (sampleUsed < 0 || sampleUsed > sampleTotal) continue;
    used += sampleUsed;
    total += sampleTotal;
    reporting += 1;
  }
  return {
    percent: total > 0 ? (used / total) * 100 : null,
    used,
    total,
    rxBps: 0,
    txBps: 0,
    reporting,
  };
}

function sampleFromPoint(point: HistoryPoint, series: HistorySeries): MetricSample {
  const cpuCount =
    positive(point.cpuCount) ?? positive(point.cores?.length) ?? positive(series.cpuCount);
  const memoryTotal = positive(point.memTotalBytes) ?? positive(series.memoryTotalBytes);
  const diskTotal = positive(point.diskTotalBytes) ?? positive(series.diskTotalBytes);
  const diskUsed =
    finite(point.diskUsedBytes) ??
    (diskTotal != null && finite(point.disk) != null ? (point.disk / 100) * diskTotal : undefined);
  return {
    cpuPercent: finite(point.cpu),
    logicalCpus: cpuCount,
    memoryUsedBytes: finite(point.memUsedBytes),
    memoryTotalBytes: memoryTotal,
    diskUsedBytes: diskUsed,
    diskTotalBytes: diskTotal,
    netRxBps: finite(point.netRx),
    netTxBps: finite(point.netTx),
  };
}

function latestInBucket(points: HistoryPoint[], start: number, end: number) {
  let found: HistoryPoint | undefined;
  for (const point of points) {
    if (point.t <= start || point.t > end) continue;
    if (!found || point.t >= found.t) found = point;
  }
  return found;
}

function finite(value: number | undefined): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function positive(value: number | undefined): number | undefined {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : undefined;
}
