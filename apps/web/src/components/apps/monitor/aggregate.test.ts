import { describe, expect, it } from "vitest";
import {
  aggregateCpu,
  aggregateDisk,
  aggregateHistory,
  aggregateMemory,
  aggregateNetwork,
  sparkHistory,
} from "@/src/components/apps/monitor/aggregate";
import type { HistoryPoint } from "@/src/components/apps/monitor/format";

const gb = 1024 ** 3;

describe("monitor aggregation", () => {
  it("weights CPU by logical CPU count", () => {
    const result = aggregateCpu([
      { cpuPercent: 70, logicalCpus: 8 },
      { cpuPercent: 30, logicalCpus: 4 },
      { cpuPercent: 40, logicalCpus: 16 },
    ]);
    expect(result.percent).toBeCloseTo(47.142857, 5);
    expect(result.used).toBeCloseTo(13.2, 5);
    expect(result.total).toBe(28);
    expect(result.reporting).toBe(3);
  });

  it("does not treat a missing CPU capacity as zero", () => {
    const result = aggregateCpu([{ cpuPercent: 70, logicalCpus: 8 }, { cpuPercent: 10 }]);
    expect(result.reporting).toBe(1);
    expect(result.percent).toBeCloseTo(70, 5);
  });

  it("sums memory bytes instead of averaging percentages", () => {
    const result = aggregateMemory([
      { memoryUsedBytes: 6 * gb, memoryTotalBytes: 8 * gb },
      { memoryUsedBytes: 4 * gb, memoryTotalBytes: 16 * gb },
      { memoryUsedBytes: 24 * gb, memoryTotalBytes: 32 * gb },
    ]);
    expect(result.used).toBe(34 * gb);
    expect(result.total).toBe(56 * gb);
    expect(result.percent).toBeCloseTo(60.714285, 4);
  });

  it("sums one filesystem per server and skips an impossible disk reading", () => {
    const result = aggregateDisk([
      { diskUsedBytes: 100, diskTotalBytes: 200 },
      { diskUsedBytes: 50, diskTotalBytes: 100 },
      { diskUsedBytes: 500, diskTotalBytes: 100 },
    ]);
    expect(result.reporting).toBe(2);
    expect(result.used).toBe(150);
    expect(result.total).toBe(300);
    expect(result.percent).toBeCloseTo(50, 5);
  });

  it("sums throughput and leaves a missing rate out", () => {
    const result = aggregateNetwork([
      { netRxBps: 1_000, netTxBps: 400 },
      { netRxBps: 0, netTxBps: 25 },
      { netRxBps: 50 },
    ]);
    expect(result.reporting).toBe(2);
    expect(result.rxBps).toBe(1_000);
    expect(result.txBps).toBe(425);
  });

  it("aligns history by time and skips a bucket a server missed", () => {
    const now = 1_000_000;
    const point = (t: number, cpu: number, cpus: number): HistoryPoint => ({
      t,
      cpu,
      cpuUser: 0,
      cpuSystem: 0,
      cpuIowait: 0,
      cpuCount: cpus,
      memory: 0,
      memUsedBytes: 1,
      memTotalBytes: 2,
      memCachedBytes: 0,
      memBuffersBytes: 0,
      memAvailableBytes: 1,
      disk: 0,
      diskUsedBytes: 1,
      diskTotalBytes: 2,
      diskReadBps: 0,
      diskWriteBps: 0,
      netRx: 10,
      netTx: 5,
      svcRunning: 0,
      svcStopped: 0,
      svcFailed: 0,
      svcOther: 0,
    });
    const history = aggregateHistory(
      [{ points: [point(now - 1_000, 70, 8)] }, { points: [point(now - 1_000, 40, 16)] }],
      60_000,
      now,
    );
    const last = history[history.length - 1];
    expect(last.cpu).toBeCloseTo((8 * 70 + 16 * 40) / 24, 5);
    expect(last.netRx).toBe(20);
  });

  it("keeps a sparkline point for every sample", () => {
    const now = 1_000_000;
    const point = (t: number, cpu: number): HistoryPoint => ({
      t,
      cpu,
      cpuUser: 0,
      cpuSystem: 0,
      cpuIowait: 0,
      memory: 40,
      memUsedBytes: 4,
      memCachedBytes: 0,
      memBuffersBytes: 0,
      memAvailableBytes: 6,
      disk: 20,
      diskReadBps: 0,
      diskWriteBps: 0,
      netRx: 1_000 + cpu,
      netTx: 100,
      svcRunning: 0,
      svcStopped: 0,
      svcFailed: 0,
      svcOther: 0,
    });
    const points = Array.from({ length: 48 }, (_, index) =>
      point(now - (48 - index) * 2_000, 30 + (index % 9)),
    );
    const spark = sparkHistory([
      { points, cpuCount: 8, memoryTotalBytes: 10, diskTotalBytes: 100, diskUsedBytes: 20 },
    ]);
    expect(spark).toHaveLength(48);
    expect(new Set(spark.map((item) => item.cpu)).size).toBeGreaterThan(1);
    expect(spark[0].cpu).toBeCloseTo(30, 5);
  });
});
