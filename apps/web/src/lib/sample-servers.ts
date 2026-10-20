import type { ServerInfo } from "@/src/lib/api/server";
import type { Server } from "@/src/lib/servers";

type SampleSpec = {
  id: string;
  name: string;
  host: string;
  iconId: string;
  connected: boolean;
  error?: string;
  cpu: number;
  memory: number;
  disk: number;
  cores: number;
  memoryGb: number;
  diskGb: number;
  net: number;
};

/** Fictional hosts so the multi-server monitor can be exercised without extra SSH sessions. */
const SAMPLES: SampleSpec[] = [
  {
    id: "sample_northwind",
    name: "Sample Northwind",
    host: "203.0.113.10",
    iconId: "production",
    connected: true,
    cpu: 42,
    memory: 61,
    disk: 48,
    cores: 8,
    memoryGb: 32,
    diskGb: 512,
    net: 1_800_000,
  },
  {
    id: "sample_lakeside",
    name: "Sample Lakeside",
    host: "203.0.113.20",
    iconId: "homelab",
    connected: true,
    cpu: 18,
    memory: 44,
    disk: 71,
    cores: 4,
    memoryGb: 16,
    diskGb: 256,
    net: 240_000,
  },
  {
    id: "sample_harbor",
    name: "Sample Harbor",
    host: "203.0.113.30",
    iconId: "ai-gpu",
    connected: true,
    cpu: 76,
    memory: 83,
    disk: 36,
    cores: 16,
    memoryGb: 128,
    diskGb: 1024,
    net: 8_400_000,
  },
  {
    id: "sample_relay",
    name: "Sample Relay",
    host: "203.0.113.40",
    iconId: "staging",
    connected: false,
    error: "authentication failed",
    cpu: 0,
    memory: 0,
    disk: 0,
    cores: 2,
    memoryGb: 8,
    diskGb: 64,
    net: 0,
  },
];

function samplesEnabled() {
  return process.env.NODE_ENV === "development";
}

export function isSampleServer(id: string) {
  return samplesEnabled() && SAMPLES.some((item) => item.id === id);
}

export function mergeSampleServers(servers: Server[], enabled = samplesEnabled()): Server[] {
  if (!enabled) return servers;
  const extras = SAMPLES.filter((item) => !servers.some((server) => server.id === item.id)).map(
    toSession,
  );
  return extras.length === 0 ? servers : [...servers, ...extras];
}

export function sampleMetrics(id: string, now = Date.now()): ServerInfo | null {
  if (!isSampleServer(id)) return null;
  return previewMetrics(id, now);
}

/** Metrics for a sample id, including when the development gate is off. */
export function previewMetrics(id: string, now = Date.now()): ServerInfo | null {
  const spec = SAMPLES.find((item) => item.id === id);
  if (!spec) return null;
  return buildMetrics(spec, now);
}

function toSession(spec: SampleSpec): Server {
  return {
    id: spec.id,
    name: spec.name,
    hostname: spec.name.toLowerCase().replace(/[^a-z0-9]+/g, "-"),
    address: spec.host,
    status: spec.connected ? "online" : "offline",
    connected: spec.connected,
    username: "deploy",
    sshPort: 22,
    authType: "password",
    iconId: spec.iconId,
    cpuUsage: spec.connected ? spec.cpu : undefined,
    memoryUsage: spec.connected ? spec.memory : undefined,
    diskUsage: spec.connected ? spec.disk : undefined,
    error: spec.error,
  };
}

function buildMetrics(spec: SampleSpec, now: number): ServerInfo {
  const history = Array.from({ length: 60 }, (_, index) => {
    const t = now - (59 - index) * 2000;
    const cpu = wave(t, spec.cpu, 8, 50_000, 0.4, 96);
    const memory = wave(t, spec.memory, 4, 70_000, 1.2, 96);
    const disk = spec.disk;
    return {
      t,
      cpu,
      cpuUser: cpu * 0.62,
      cpuSystem: cpu * 0.28,
      cpuIowait: cpu * 0.1,
      memory,
      memUsedBytes: Math.round((memory / 100) * spec.memoryGb * 1024 ** 3),
      disk,
      diskReadBps: wave(t, spec.net * 0.15, spec.net * 0.08, 40_000, 0.2),
      diskWriteBps: wave(t, spec.net * 0.05, spec.net * 0.03, 55_000, 1.4),
      netRx: wave(t, spec.net, spec.net * 0.35, 30_000, 0.6),
      netTx: wave(t, spec.net * 0.22, spec.net * 0.1, 36_000, 2.1),
    };
  });
  const latest = history[history.length - 1];
  const memTotal = spec.memoryGb * 1024 ** 3;
  const diskTotal = spec.diskGb * 1024 ** 3;
  return {
    id: spec.id,
    name: spec.name,
    hostname: spec.name.toLowerCase().replace(/[^a-z0-9]+/g, "-"),
    status: spec.connected ? "online" : "offline",
    host: spec.host,
    port: 22,
    username: "deploy",
    authType: "password",
    iconId: spec.iconId,
    connected: spec.connected,
    cpuUsage: latest.cpu,
    cpuUser: latest.cpuUser,
    cpuSystem: latest.cpuSystem,
    cpuIowait: latest.cpuIowait,
    memoryUsage: latest.memory,
    diskUsage: latest.disk,
    uptimeSeconds: 86_400 * 12,
    cpuCores: spec.cores,
    cpuPhysicalCores: Math.max(1, spec.cores / 2),
    cpuModel: "Sample CPU",
    memoryUsedBytes: latest.memUsedBytes,
    memoryTotalBytes: memTotal,
    memoryAvailableBytes: memTotal - latest.memUsedBytes,
    diskUsedBytes: Math.round((spec.disk / 100) * diskTotal),
    diskTotalBytes: diskTotal,
    diskReadBps: latest.diskReadBps,
    diskWriteBps: latest.diskWriteBps,
    netRxBps: latest.netRx,
    netTxBps: latest.netTx,
    osName: "Sample Linux",
    kernel: "6.8.0-sample",
    arch: "x86_64",
    load1: (latest.cpu / 100) * spec.cores,
    load5: (spec.cpu / 100) * spec.cores,
    load15: (spec.cpu / 100) * spec.cores * 0.8,
    processCount: 3,
    processes: [
      { pid: 1101, name: "node", cpu: latest.cpu * 0.45, memory: 4.2, memoryBytes: 720_000_000 },
      {
        pid: 842,
        name: "postgres",
        cpu: latest.cpu * 0.2,
        memory: 6.1,
        memoryBytes: 1_100_000_000,
      },
      { pid: 1, name: "systemd", cpu: 0.2, memory: 0.3, memoryBytes: 18_000_000 },
    ],
    serviceRunning: 2,
    serviceStopped: 1,
    serviceFailed: 0,
    serviceOther: 0,
    services: [
      { name: "ssh", state: "running", description: "Secure shell" },
      { name: "docker", state: "running", description: "Containers" },
      { name: "sample-backup", state: "stopped", description: "Preview job" },
    ],
    cores: Array.from({ length: spec.cores }, (_, id) => ({
      id,
      usage: wave(now, latest.cpu, 12, 20_000, id, 96),
    })),
    history,
    error: spec.error,
  };
}

function wave(
  t: number,
  base: number,
  amp: number,
  period: number,
  phase: number,
  max = Number.POSITIVE_INFINITY,
) {
  const value = base + Math.sin((t / period) * Math.PI * 2 + phase) * amp;
  return Math.round(Math.min(max, Math.max(0, value)) * 10) / 10;
}
