"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  Activity,
  Box,
  Check,
  ChevronDown,
  Cpu,
  HardDrive,
  List,
  MemoryStick,
  MoreHorizontal,
  Server,
  Wifi,
} from "lucide-react";
import {
  AreaChart,
  ServerLineChart,
  Sparkline,
  SparklineValues,
  type ServerLine,
} from "@/src/components/apps/monitor/charts";
import { ServerIcon } from "@/src/components/servers/ServerIcon";
import { CpuTab } from "@/src/components/apps/monitor/CpuTab";
import { DiskTab } from "@/src/components/apps/monitor/DiskTab";
import { MemoryTab } from "@/src/components/apps/monitor/MemoryTab";
import { NetworkTab } from "@/src/components/apps/monitor/NetworkTab";
import { ProcessesTab } from "@/src/components/apps/monitor/ProcessesTab";
import { ServicesTab } from "@/src/components/apps/monitor/ServicesTab";
import {
  aggregateCpu,
  aggregateDisk,
  sparkHistory,
  aggregateMemory,
  aggregateNetwork,
  sampleFromInfo,
  type HistorySeries,
  type MetricSample,
} from "@/src/components/apps/monitor/aggregate";
import {
  filterHistory,
  formatBytes,
  formatRate,
  formatUptime,
  mergeHistory,
  type HistoryPoint,
} from "@/src/components/apps/monitor/format";
import { getServerMetrics, type MonitorProcess, type ServerInfo } from "@/src/lib/api/server";
import { useOptionalServer } from "@/src/lib/api/server-context";
import { connectionStatus, type Server as ServerRecord } from "@/src/lib/servers";
import { useSelectedServer, useSession } from "@/src/lib/session";

type Tab = "overview" | "cpu" | "memory" | "disk" | "network" | "processes" | "services";
type RangeId = "1m" | "5m" | "1h";
type ProcessSort = "cpu" | "memory";

const RANGE_MS: Record<RangeId, number> = {
  "1m": 60_000,
  "5m": 5 * 60_000,
  "1h": 60 * 60_000,
};

const TABS: { id: Tab; label: string; icon: typeof Cpu }[] = [
  { id: "overview", label: "Overview", icon: Activity },
  { id: "cpu", label: "CPU", icon: Cpu },
  { id: "memory", label: "Memory", icon: MemoryStick },
  { id: "disk", label: "Disk", icon: HardDrive },
  { id: "network", label: "Network", icon: Wifi },
  { id: "processes", label: "Processes", icon: List },
  { id: "services", label: "Services", icon: Box },
];

const TAB_SELECTED = "#2b7fff";
const LINE_COLORS = [
  "#34d399",
  "#60a5fa",
  "#c084fc",
  "#f472b6",
  "#fbbf24",
  "#fb7185",
  "#22d3ee",
  "#a3e635",
  "#f97316",
  "#818cf8",
];

type SelectionMode = "all" | "custom";

export function DashboardApp() {
  const bound = useSelectedServer();
  const { servers, loadingServers } = useSession();
  const optional = useOptionalServer();
  const selection = useMonitorSelection(bound, servers);
  const samples = useMonitorSamples(selection.pollIds);
  const [tab, setTab] = useState<Tab>("overview");
  const [range, setRange] = useState<RangeId>("1h");
  const single = selection.visible.length === 1 ? selection.visible[0] : null;
  const focusSnap = single ? resolveSnap(single, samples.live[single.id], optional) : null;
  const subtitle =
    selection.visible.length > 1
      ? "Live metrics for the selected servers."
      : single
        ? tabCopy(
            tab,
            monitorOnline(single, focusSnap),
            Boolean(optional?.loading && optional.server?.id === single.id),
            (optional?.server?.id === single.id ? optional.error : null) ||
              focusSnap?.error ||
              null,
          )
        : "Select a connected server to see metrics.";

  return (
    <div className="sui-app flex h-full min-h-0 overflow-hidden">
      <MonitorSidebar tab={tab} onTab={setTab} />
      <div className="min-h-0 min-w-0 flex-1 overflow-auto px-5 py-4">
        {single?.connected === false ? <DisconnectedBanner server={single} /> : null}
        <MonitorHeader
          tab={tab}
          range={range}
          onRange={setRange}
          subtitle={subtitle}
          menu={
            <ServerMenu
              label={selection.label}
              mode={selection.mode}
              catalog={selection.catalog}
              checked={selection.checked}
              onToggle={selection.toggle}
              onAll={selection.selectAll}
            />
          }
        />
        {loadingServers ? (
          <p className="mt-8 text-[13px] text-white/55">Loading servers…</p>
        ) : selection.visible.length === 0 ? (
          <p className="mt-8 text-[13px] text-white/55">
            {selection.mode === "all"
              ? "No servers are configured. Metrics appear here when a server is added."
              : "Select a server from the menu to see metrics."}
          </p>
        ) : selection.visible.length === 1 ? (
          <SingleMonitor
            server={selection.visible[0]}
            tab={tab}
            range={range}
            onRange={setRange}
            samples={samples}
          />
        ) : (
          <MultiMonitor
            servers={selection.visible}
            tab={tab}
            range={range}
            onRange={setRange}
            samples={samples}
            onOpen={selection.selectOne}
          />
        )}
      </div>
    </div>
  );
}

type Samples = {
  live: Record<string, ServerInfo>;
  history: Record<string, HistoryPoint[]>;
  refresh: (id: string) => void;
};

type Selection = {
  mode: SelectionMode;
  label: string;
  catalog: ServerRecord[];
  visible: ServerRecord[];
  checked: Set<string>;
  pollIds: string[];
  selectAll: () => void;
  selectOne: (id: string) => void;
  toggle: (id: string) => void;
};

function useMonitorSelection(bound: ServerRecord | null, servers: ServerRecord[]): Selection {
  const [mode, setMode] = useState<SelectionMode>(bound?.id ? "custom" : "all");
  const [picked, setPicked] = useState<string[]>(() => (bound?.id ? [bound.id] : []));

  const catalog =
    bound && !servers.some((item) => item.id === bound.id) ? [bound, ...servers] : servers;
  const catalogIds = catalog.map((item) => item.id);

  const visible = mode === "all" ? catalog : catalog.filter((item) => picked.includes(item.id));

  const checked = new Set(mode === "all" ? catalogIds : visible.map((item) => item.id));

  const label =
    mode === "all"
      ? `All servers (${catalog.length})`
      : visible.length === 0
        ? "No servers"
        : visible.length === 1
          ? visible[0].name
          : `${visible.length} servers`;

  const pollIds = visible
    .filter((item) => connectionStatus(item) === "online")
    .map((item) => item.id);

  function selectAll() {
    setMode("all");
  }

  function selectOne(id: string) {
    setMode("custom");
    setPicked([id]);
  }

  function toggle(id: string) {
    if (!catalogIds.includes(id)) return;
    setMode("custom");
    setPicked(() => {
      const base = mode === "all" ? catalogIds : picked.filter((item) => catalogIds.includes(item));
      return base.includes(id) ? base.filter((item) => item !== id) : [...base, id];
    });
  }

  return { mode, label, catalog, visible, checked, pollIds, selectAll, selectOne, toggle };
}

function useMonitorSamples(ids: string[]): Samples {
  const key = ids.join("\0");
  const [live, setLive] = useState<Record<string, ServerInfo>>({});
  const [history, setHistory] = useState<Record<string, HistoryPoint[]>>({});

  const refresh = useCallback((id: string) => {
    void (async () => {
      try {
        const next = await getServerMetrics(id);
        if (!isServerInfo(next)) return;
        rememberSample(next, setLive, setHistory);
      } catch {
        /* keep the last good sample */
      }
    })();
  }, []);

  useEffect(() => {
    const pollIds = key ? key.split("\0") : [];
    if (pollIds.length === 0) return undefined;
    let cancelled = false;
    let timer = 0;

    const tick = async () => {
      const queue = [...pollIds];
      const workers = Array.from({ length: Math.min(4, queue.length) }, async () => {
        while (!cancelled && queue.length > 0) {
          const id = queue.shift();
          if (!id) return;
          try {
            const next = await getServerMetrics(id);
            if (cancelled || !isServerInfo(next) || next.id !== id) continue;
            rememberSample(next, setLive, setHistory);
          } catch {
            /* keep the last good sample */
          }
        }
      });
      await Promise.all(workers);
      if (!cancelled) timer = window.setTimeout(() => void tick(), 2000);
    };

    void tick();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [key]);

  return { live, history, refresh };
}

function rememberSample(
  next: ServerInfo,
  setLive: (value: (prev: Record<string, ServerInfo>) => Record<string, ServerInfo>) => void,
  setHistory: (
    value: (prev: Record<string, HistoryPoint[]>) => Record<string, HistoryPoint[]>,
  ) => void,
) {
  setLive((prev) => ({ ...prev, [next.id]: next }));
  setHistory((prev) => ({
    ...prev,
    [next.id]: mergeHistory(prev[next.id] || [], mapHistory(next.history), liveSample(next)),
  }));
}

function isServerInfo(value: unknown): value is ServerInfo {
  return (
    Boolean(value) && typeof value === "object" && typeof (value as ServerInfo).id === "string"
  );
}

function mapHistory(points: ServerInfo["history"]): HistoryPoint[] {
  return (points || []).map((point) => ({
    t: point.t,
    cpu: point.cpu || 0,
    cpuUser: point.cpuUser || 0,
    cpuSystem: point.cpuSystem || 0,
    cpuIowait: point.cpuIowait || 0,
    memory: point.memory || 0,
    memUsedBytes: point.memUsedBytes || 0,
    memCachedBytes: point.memCachedBytes || 0,
    memBuffersBytes: point.memBuffersBytes || 0,
    memAvailableBytes: point.memAvailableBytes || 0,
    disk: point.disk || 0,
    diskReadBps: point.diskReadBps || 0,
    diskWriteBps: point.diskWriteBps || 0,
    netRx: point.netRx || 0,
    netTx: point.netTx || 0,
    svcRunning: point.svcRunning || 0,
    svcStopped: point.svcStopped || 0,
    svcFailed: point.svcFailed || 0,
    svcOther: point.svcOther || 0,
    cores: point.cores,
  }));
}

function liveSample(next: ServerInfo): HistoryPoint {
  return {
    t: Date.now(),
    cpu: next.cpuUsage || 0,
    cpuUser: next.cpuUser || 0,
    cpuSystem: next.cpuSystem || 0,
    cpuIowait: next.cpuIowait || 0,
    memory: next.memoryUsage || 0,
    memUsedBytes: next.memoryUsedBytes || 0,
    memCachedBytes: next.memoryCachedBytes || 0,
    memBuffersBytes: next.memoryBuffersBytes || 0,
    memAvailableBytes: Math.max(
      0,
      (next.memoryTotalBytes || 0) -
        (next.memoryUsedBytes || 0) -
        (next.memoryCachedBytes || 0) -
        (next.memoryBuffersBytes || 0),
    ),
    disk: next.diskUsage || 0,
    diskReadBps: next.diskReadBps || 0,
    diskWriteBps: next.diskWriteBps || 0,
    diskUsedBytes: positiveSample(next.diskUsedBytes),
    diskTotalBytes: positiveSample(next.diskTotalBytes),
    netRx: next.netRxBps || 0,
    netTx: next.netTxBps || 0,
    svcRunning: next.serviceRunning || 0,
    svcStopped: next.serviceStopped || 0,
    svcFailed: next.serviceFailed || 0,
    svcOther: next.serviceOther || 0,
    cores: (next.cores || []).map((core) => core.usage),
    cpuCount: positiveSample(next.cpuCores) ?? positiveSample(next.cores?.length),
    memTotalBytes: positiveSample(next.memoryTotalBytes),
  };
}

function positiveSample(value: number | undefined) {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : undefined;
}

function seedSnap(server: ServerRecord): ServerInfo {
  return {
    id: server.id,
    name: server.name,
    hostname: server.hostname,
    status: server.connected ? "online" : "offline",
    host: server.address,
    username: server.username || "",
    cpuUsage: server.cpuUsage || 0,
    memoryUsage: server.memoryUsage || 0,
    diskUsage: server.diskUsage || 0,
    uptimeSeconds: 0,
    error: server.metricsError,
  };
}

function resolveSnap(
  server: ServerRecord,
  live: ServerInfo | undefined,
  optional: { server: ServerInfo | null } | null,
): ServerInfo {
  if (live) return live;
  if (optional?.server?.id === server.id) return optional.server;
  return seedSnap(server);
}

function monitorOnline(server: ServerRecord, snap: ServerInfo | null) {
  return server.connected !== false && snap?.status === "online";
}

function seriesColor(serverId: string, catalog: ServerRecord[]) {
  const index = Math.max(
    0,
    catalog.findIndex((item) => item.id === serverId),
  );
  return LINE_COLORS[index % LINE_COLORS.length];
}

function chartFor(server: ServerRecord, samples: Samples, range: RangeId) {
  return filterHistory(samples.history[server.id] || [], RANGE_MS[range]);
}

function MonitorSidebar({ tab, onTab }: { tab: Tab; onTab: (tab: Tab) => void }) {
  return (
    <aside className="sui-sidebar flex w-[188px] shrink-0 flex-col px-3 py-4">
      <div className="mb-4 flex items-center gap-2 px-2">
        <span className="flex size-8 items-center justify-center rounded-xl bg-[#2b7fff]/20 text-[#6ea8ff]">
          <Activity className="size-4" />
        </span>
        <p className="text-[13px] font-semibold tracking-tight">System Monitor</p>
      </div>
      <nav aria-label="Monitor sections" className="flex flex-col gap-0.5">
        {TABS.map((item) => {
          const Icon = item.icon;
          const active = tab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              aria-current={active ? "page" : undefined}
              onClick={() => onTab(item.id)}
              className={`flex items-center gap-2.5 rounded-xl px-2.5 py-2 text-left text-[13px] outline-none transition ${
                active ? "text-white" : "text-white/70 hover:bg-white/6 hover:text-white"
              }`}
              style={
                active
                  ? { background: TAB_SELECTED, boxShadow: `0 8px 24px ${TAB_SELECTED}47` }
                  : undefined
              }
            >
              <Icon className="size-4 opacity-90" strokeWidth={1.8} />
              {item.label}
            </button>
          );
        })}
      </nav>
    </aside>
  );
}

function MonitorHeader({
  tab,
  range,
  onRange,
  subtitle,
  menu,
}: {
  tab: Tab;
  range: RangeId;
  onRange: (range: RangeId) => void;
  subtitle: string;
  menu: ReactNode;
}) {
  const TabIcon = TABS.find((item) => item.id === tab)?.icon || Activity;
  return (
    <header className="flex items-start justify-between gap-3">
      <div className="flex items-center gap-3">
        <span className="flex size-10 items-center justify-center rounded-2xl bg-[#2b7fff]/20 text-[#8eb6ff]">
          <TabIcon className="size-5" />
        </span>
        <div>
          <h3 className="text-[20px] font-semibold tracking-tight">
            {tab === "overview" ? "System Overview" : TABS.find((item) => item.id === tab)?.label}
          </h3>
          <p className="text-[12px] text-white/45">{subtitle}</p>
        </div>
      </div>
      <div className="flex items-center gap-2">
        {menu}
        {tab !== "processes" && tab !== "services" ? (
          <>
            <label className="sr-only" htmlFor="monitor-range">
              Time range
            </label>
            <select
              id="monitor-range"
              value={range}
              onChange={(event) => onRange(event.target.value as RangeId)}
              className="rounded-lg border border-white/10 bg-white/6 px-2.5 py-1.5 text-[12px] text-white/80 outline-none"
            >
              <option value="1m">Last 1 Minute</option>
              <option value="5m">Last 5 Minutes</option>
              <option value="1h">Last 1 Hour</option>
            </select>
          </>
        ) : null}
        <button
          type="button"
          className="rounded-lg border border-white/10 bg-white/6 p-1.5 text-white/70"
          aria-label="More"
        >
          <MoreHorizontal className="size-4" />
        </button>
      </div>
    </header>
  );
}

function ServerMenu({
  label,
  mode,
  catalog,
  checked,
  onToggle,
  onAll,
}: {
  label: string;
  mode: SelectionMode;
  catalog: ServerRecord[];
  checked: Set<string>;
  onToggle: (id: string) => void;
  onAll: () => void;
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return undefined;
    function onPointer(event: MouseEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="inline-flex max-w-[220px] items-center gap-1.5 rounded-lg border border-white/10 bg-white/6 px-2.5 py-1.5 text-[12px] text-white/85 outline-none hover:bg-white/10"
      >
        <span className="truncate">{label}</span>
        <ChevronDown className="size-3.5 shrink-0 opacity-70" aria-hidden />
      </button>
      {open ? (
        <div
          role="menu"
          aria-label="Servers"
          className="sui-menu absolute right-0 top-[calc(100%+6px)] z-30 w-[280px] overflow-hidden rounded-2xl border py-1 shadow-[0_18px_50px_rgba(0,0,0,0.45)]"
        >
          <button
            type="button"
            role="menuitemcheckbox"
            aria-checked={mode === "all"}
            onClick={onAll}
            className="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-white/90 hover:bg-white/8"
          >
            <span className="min-w-0 flex-1 truncate">All servers</span>
            {mode === "all" ? (
              <Check className="size-3.5 shrink-0" aria-hidden />
            ) : (
              <span className="size-3.5" />
            )}
          </button>
          <div className="my-1 h-px bg-white/10" />
          {catalog.length === 0 ? (
            <p className="px-3 py-2 text-[12px] text-white/55">No servers yet.</p>
          ) : (
            catalog.map((server) => {
              const status = connectionStatus(server);
              const online = status === "online";
              const isChecked = checked.has(server.id);
              return (
                <button
                  key={server.id}
                  type="button"
                  role="menuitemcheckbox"
                  aria-checked={isChecked}
                  onClick={() => onToggle(server.id)}
                  className={`flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] hover:bg-white/8 ${
                    online ? "text-white/90" : "text-white/55"
                  }`}
                >
                  <ServerIcon
                    iconId={server.iconId}
                    customIcon={server.customIcon}
                    serverId={server.id}
                    updatedAt={server.updatedAt}
                    size={18}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate">{server.name}</span>
                    <span className="block truncate text-[11px] text-white/45">
                      {monitorStatusLabel(status)}
                      {!online && server.error ? ` · ${server.error}` : ""}
                    </span>
                  </span>
                  {isChecked ? (
                    <Check className="size-3.5 shrink-0" aria-hidden />
                  ) : (
                    <span className="size-3.5 shrink-0" />
                  )}
                </button>
              );
            })
          )}
        </div>
      ) : null}
    </div>
  );
}

function monitorStatusLabel(status: ReturnType<typeof connectionStatus>) {
  if (status === "online") return "Connected";
  if (status === "connecting") return "Connecting";
  if (status === "reconnecting") return "Reconnecting";
  if (status === "authentication_failed") return "Authentication failed";
  if (status === "error") return "Connection error";
  return "Disconnected";
}

function DisconnectedBanner({ server }: { server: ServerRecord }) {
  const { connectServer } = useSession();
  return (
    <div className="mb-4 flex items-center justify-between gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3">
      <p className="text-[13px] text-white/70">
        {server.name} is disconnected. Metrics stay empty until this server connects.
      </p>
      <button
        type="button"
        onClick={() => void connectServer(server.id)}
        className="shrink-0 rounded-full bg-white px-3 py-1.5 text-[12px] font-semibold text-zinc-900"
      >
        Connect
      </button>
    </div>
  );
}

function SingleMonitor({
  server,
  tab,
  range,
  onRange,
  samples,
}: {
  server: ServerRecord;
  tab: Tab;
  range: RangeId;
  onRange: (range: RangeId) => void;
  samples: Samples;
}) {
  const optional = useOptionalServer();
  const [processSort, setProcessSort] = useState<ProcessSort>("cpu");
  const snap = resolveSnap(server, samples.live[server.id], optional);
  const online = monitorOnline(server, snap);
  const chart = chartFor(server, samples, range);
  const processes = useMemo(() => {
    const list = [...(snap.processes || [])];
    list.sort((a, b) => (processSort === "cpu" ? b.cpu - a.cpu : b.memory - a.memory));
    return list;
  }, [snap.processes, processSort]);
  const rangeLabel =
    range === "1m" ? "in last minute" : range === "5m" ? "in last 5 minutes" : "in last hour";

  return (
    <>
      {tab === "overview" ? (
        <Overview
          snap={snap}
          online={online}
          chart={chart}
          processes={processes}
          processSort={processSort}
          onSort={setProcessSort}
        />
      ) : null}
      {tab === "cpu" ? <CpuTab snap={snap} online={online} chart={chart} /> : null}
      {tab === "memory" ? <MemoryTab snap={snap} online={online} chart={chart} /> : null}
      {tab === "disk" ? <DiskTab snap={snap} online={online} chart={chart} /> : null}
      {tab === "network" ? (
        <NetworkTab snap={snap} online={online} chart={chart} rangeLabel={rangeLabel} />
      ) : null}
      {tab === "processes" ? (
        <ProcessesTab snap={snap} online={online} chart={chart} range={range} onRange={onRange} />
      ) : null}
      {tab === "services" ? (
        <ServicesTab
          snap={snap}
          online={online}
          chart={chart}
          onRefresh={() => samples.refresh(server.id)}
        />
      ) : null}
    </>
  );
}

function MultiMonitor({
  servers,
  tab,
  range,
  onRange,
  samples,
  onOpen,
}: {
  servers: ServerRecord[];
  tab: Tab;
  range: RangeId;
  onRange: (range: RangeId) => void;
  samples: Samples;
  onOpen: (id: string) => void;
}) {
  const { servers: catalog } = useSession();
  const [focusId, setFocusId] = useState(servers[0]?.id || "");
  const onlineServers = servers.filter((item) => connectionStatus(item) === "online");
  const switchServers = onlineServers.length > 0 ? onlineServers : servers;
  const activeId = switchServers.some((item) => item.id === focusId)
    ? focusId
    : switchServers[0]?.id || "";
  const focus = servers.find((item) => item.id === activeId) || servers[0];
  const reporting = servers
    .map((server) => sampleFromInfo(samples.live[server.id]))
    .filter((sample): sample is NonNullable<typeof sample> => sample != null);
  const historySeries: HistorySeries[] = servers.map((server) => {
    const live = samples.live[server.id];
    return {
      points: chartFor(server, samples, range),
      cpuCount: positiveSample(live?.cpuCores) ?? positiveSample(live?.cores?.length),
      memoryTotalBytes: positiveSample(live?.memoryTotalBytes),
      diskUsedBytes: positiveSample(live?.diskUsedBytes),
      diskTotalBytes: positiveSample(live?.diskTotalBytes),
    };
  });
  const combined = sparkHistory(historySeries);

  if (tab === "overview") {
    return (
      <MultiOverview
        servers={servers}
        reporting={reporting}
        combined={combined}
        samples={samples}
        range={range}
        onOpen={onOpen}
      />
    );
  }

  if (tab === "processes" || tab === "services") {
    const snap = focus ? resolveSnap(focus, samples.live[focus.id], null) : null;
    const chart = focus ? chartFor(focus, samples, range) : [];
    return (
      <div className="mt-4">
        <p className="mb-3 text-[12px] text-white/55">
          {focus ? `Actions apply only to ${focus.name}.` : "Select a server."}
        </p>
        <ServerSwitch servers={switchServers} value={focus?.id || ""} onChange={setFocusId} />
        {tab === "processes" && focus ? (
          <ProcessesTab snap={snap} online chart={chart} range={range} onRange={onRange} />
        ) : null}
        {tab === "services" && focus ? (
          <ServicesTab
            snap={snap}
            online
            chart={chart}
            onRefresh={() => samples.refresh(focus.id)}
          />
        ) : null}
      </div>
    );
  }

  const metric = tab as LineMetric;
  const palette = catalog.length > 0 ? catalog : servers;
  const lines = serverLines(servers, samples, range, palette, metric);

  return (
    <MultiMetric
      metric={metric}
      servers={servers}
      lines={lines}
      reporting={reporting}
      samples={samples}
    />
  );
}

type LineMetric = "cpu" | "memory" | "disk" | "network";

const LINE_META: Record<LineMetric, { title: string; scale: "percent" | "rate" }> = {
  cpu: { title: "CPU Usage History", scale: "percent" },
  memory: { title: "Memory Usage", scale: "percent" },
  disk: { title: "Disk Usage", scale: "percent" },
  network: { title: "Network Usage", scale: "rate" },
};

const LINE_LABEL: Record<LineMetric, string> = {
  cpu: "CPU",
  memory: "Memory",
  disk: "Disk",
  network: "Network",
};

function serverLines(
  servers: ServerRecord[],
  samples: Samples,
  range: RangeId,
  catalog: ServerRecord[],
  metric: LineMetric,
): ServerLine[] {
  return servers.map((server) => ({
    id: server.id,
    label: server.name,
    color: seriesColor(server.id, catalog),
    points: chartFor(server, samples, range).map((point) => {
      if (metric === "network") {
        const rx = Number.isFinite(point.netRx) ? point.netRx : 0;
        const tx = Number.isFinite(point.netTx) ? point.netTx : 0;
        return {
          t: point.t,
          value: rx + tx,
          detail: `↓ ${formatRate(rx)} · ↑ ${formatRate(tx)}`,
        };
      }
      const value = point[metric];
      return { t: point.t, value: Number.isFinite(value) ? value : null };
    }),
  }));
}

function MultiMetric({
  metric,
  servers,
  lines,
  reporting,
  samples,
}: {
  metric: LineMetric;
  servers: ServerRecord[];
  lines: ServerLine[];
  reporting: MetricSample[];
  samples: Samples;
}) {
  return (
    <>
      <MetricCards metric={metric} servers={servers} reporting={reporting} samples={samples} />
      <ServerHistory title={LINE_META[metric].title} metric={metric} lines={lines} />
      <section className="sui-glass-card mt-3 rounded-2xl p-4">
        <p className="mb-3 text-[13px] font-medium">{LINE_LABEL[metric]} by server</p>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-4">
          {servers.map((server) => {
            const line = lines.find((item) => item.id === server.id);
            const values = (line?.points || []).flatMap((point) =>
              point.value == null ? [] : [point.value],
            );
            return (
              <div key={server.id} className="sui-glass-card rounded-xl px-2.5 py-2">
                <div className="flex items-center justify-between gap-2 text-[11px]">
                  <span className="flex min-w-0 items-center gap-1.5 text-white/70">
                    <span
                      className="size-1.5 shrink-0 rounded-full"
                      style={{ background: line?.color }}
                    />
                    <span className="truncate">{server.name}</span>
                  </span>
                  <span className="shrink-0 tabular-nums text-white/85">
                    {currentReading(metric, server, samples.live[server.id])}
                  </span>
                </div>
                <SparklineValues
                  values={values}
                  color={line?.color || "#94a3b8"}
                  className="mt-1 h-7 w-full"
                />
              </div>
            );
          })}
        </div>
      </section>
    </>
  );
}

function ServerHistory({
  title,
  metric,
  onMetric,
  lines,
}: {
  title: string;
  metric: LineMetric;
  onMetric?: (metric: LineMetric) => void;
  lines: ServerLine[];
}) {
  return (
    <section className="mt-3 overflow-hidden sui-glass-card rounded-2xl">
      <div className="flex flex-wrap items-center justify-between gap-2 px-4 pt-4">
        <p className="text-[13px] font-medium">{title}</p>
        <div className="flex flex-wrap items-center justify-end gap-3">
          {onMetric ? (
            <div
              role="tablist"
              aria-label="Chart metric"
              className="inline-flex gap-0.5 rounded-xl border border-white/10 bg-white/5 p-0.5"
            >
              {(Object.keys(LINE_LABEL) as LineMetric[]).map((item) => (
                <button
                  key={item}
                  type="button"
                  role="tab"
                  aria-selected={item === metric}
                  onClick={() => onMetric(item)}
                  className={`rounded-lg px-2.5 py-1 text-[11px] font-medium transition ${
                    item === metric ? "bg-white text-zinc-900" : "text-white/70 hover:text-white"
                  }`}
                >
                  {LINE_LABEL[item]}
                </button>
              ))}
            </div>
          ) : null}
          <ul className="flex flex-wrap gap-3 text-[11px] text-white/55">
            {lines.map((item) => (
              <li key={item.id} className="flex items-center gap-1.5">
                <span className="size-2 rounded-full" style={{ background: item.color }} />
                <span className="max-w-[140px] truncate">{item.label}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
      <div className="h-[200px] px-2 pb-2 pt-3">
        <ServerLineChart series={lines} scale={LINE_META[metric].scale} />
      </div>
    </section>
  );
}

function MetricCards({
  metric,
  servers,
  reporting,
  samples,
}: {
  metric: LineMetric;
  servers: ServerRecord[];
  reporting: MetricSample[];
  samples: Samples;
}) {
  const cpu = aggregateCpu(reporting);
  const memory = aggregateMemory(reporting);
  const disk = aggregateDisk(reporting);
  const network = aggregateNetwork(reporting);
  const busiest = metric === "network" ? null : busiestServer(servers, samples, metric);

  if (metric === "memory") {
    const available = Math.max(0, memory.total - memory.used);
    return (
      <div className="mt-4 grid grid-cols-2 gap-3 xl:grid-cols-4">
        <GlassStat
          icon={<MemoryStick className="size-3.5" />}
          tone="bg-fuchsia-500/15 text-fuchsia-300"
          label="Total Memory"
          value={memory.total > 0 ? formatBytes(memory.total) : "—"}
          detail="Across reporting servers"
        />
        <GlassStat
          icon={<MemoryStick className="size-3.5" />}
          tone="bg-sky-500/15 text-sky-300"
          label="Used Memory"
          value={memory.reporting > 0 ? formatBytes(memory.used) : "—"}
          detail={memory.total > 0 ? `${aggregatePercent(memory.percent)} of total` : "—"}
        />
        <GlassStat
          icon={<MemoryStick className="size-3.5" />}
          tone="bg-emerald-500/15 text-emerald-300"
          label="Available Memory"
          value={memory.total > 0 ? formatBytes(available) : "—"}
          detail={coverageLine(memory.reporting, servers.length) || "Physical memory"}
        />
        <GlassStat
          icon={<MemoryStick className="size-3.5" />}
          tone="bg-amber-500/15 text-amber-300"
          label="Busiest"
          value={busiest?.label || "—"}
          detail={busiest ? `${Math.round(busiest.value)}%` : "—"}
        />
      </div>
    );
  }

  if (metric === "disk") {
    const free = Math.max(0, disk.total - disk.used);
    return (
      <div className="mt-4 grid grid-cols-2 gap-3 xl:grid-cols-4">
        <GlassStat
          icon={<HardDrive className="size-3.5" />}
          tone="bg-sky-500/15 text-sky-300"
          label="Total Disk Space"
          value={disk.total > 0 ? formatBytes(disk.total) : "—"}
          detail="Root filesystems"
        />
        <GlassStat
          icon={<HardDrive className="size-3.5" />}
          tone="bg-emerald-500/15 text-emerald-300"
          label="Used Space"
          value={disk.reporting > 0 ? formatBytes(disk.used) : "—"}
          detail={disk.total > 0 ? `${aggregatePercent(disk.percent)} of total` : "—"}
        />
        <GlassStat
          icon={<HardDrive className="size-3.5" />}
          tone="bg-amber-500/15 text-amber-300"
          label="Free Space"
          value={disk.total > 0 ? formatBytes(free) : "—"}
          detail={coverageLine(disk.reporting, servers.length) || "Available capacity"}
        />
        <GlassStat
          icon={<HardDrive className="size-3.5" />}
          tone="bg-violet-500/15 text-violet-300"
          label="Busiest"
          value={busiest?.label || "—"}
          detail={busiest ? `${Math.round(busiest.value)}%` : "—"}
        />
      </div>
    );
  }

  if (metric === "network") {
    const total = network.rxBps + network.txBps;
    return (
      <div className="mt-4 grid grid-cols-2 gap-3 xl:grid-cols-4">
        <GlassStat
          icon={<Wifi className="size-3.5" />}
          tone="bg-violet-500/15 text-violet-300"
          label="Total Bandwidth"
          value={network.reporting > 0 ? formatRate(total) : "—"}
          detail="Download plus upload"
        />
        <GlassStat
          icon={<Wifi className="size-3.5" />}
          tone="bg-sky-500/15 text-sky-300"
          label="Download"
          value={network.reporting > 0 ? formatRate(network.rxBps) : "—"}
          detail={coverageLine(network.reporting, servers.length) || "Incoming"}
        />
        <GlassStat
          icon={<Wifi className="size-3.5" />}
          tone="bg-rose-500/15 text-rose-300"
          label="Upload"
          value={network.reporting > 0 ? formatRate(network.txBps) : "—"}
          detail="Outgoing"
        />
        <GlassStat
          icon={<Wifi className="size-3.5" />}
          tone="bg-emerald-500/15 text-emerald-300"
          label="Reporting"
          value={`${network.reporting} / ${servers.length}`}
          detail="Servers with throughput"
        />
      </div>
    );
  }

  return (
    <div className="mt-4 grid grid-cols-2 gap-3 xl:grid-cols-4">
      <GlassStat
        icon={<Cpu className="size-3.5" />}
        tone="bg-emerald-500/15 text-emerald-300"
        label="CPU Usage"
        value={aggregatePercent(cpu.percent)}
        detail={cpuDetailLine(cpu, servers.length)}
      />
      <GlassStat
        icon={<Cpu className="size-3.5" />}
        tone="bg-sky-500/15 text-sky-300"
        label="Logical CPUs"
        value={cpu.total ? String(cpu.total) : "—"}
        detail="Across reporting servers"
      />
      <GlassStat
        icon={<Cpu className="size-3.5" />}
        tone="bg-amber-500/15 text-amber-300"
        label="Busiest"
        value={busiest?.label || "—"}
        detail={busiest ? `${Math.round(busiest.value)}%` : "—"}
      />
      <GlassStat
        icon={<Cpu className="size-3.5" />}
        tone="bg-indigo-500/15 text-indigo-300"
        label="Reporting"
        value={`${cpu.reporting} / ${servers.length}`}
        detail="Servers with CPU capacity"
      />
    </div>
  );
}

function GlassStat({
  icon,
  tone,
  label,
  value,
  detail,
}: {
  icon: ReactNode;
  tone: string;
  label: string;
  value: string;
  detail: string;
}) {
  return (
    <article className="sui-glass-card rounded-2xl p-3.5">
      <div className="flex items-center gap-2 text-[12px] text-white/55">
        <span className={`flex size-6 items-center justify-center rounded-lg ${tone}`}>{icon}</span>
        {label}
      </div>
      <p className="mt-2 truncate text-[26px] font-semibold tabular-nums tracking-tight">{value}</p>
      <p className="truncate text-[11px] text-white/40">{detail}</p>
    </article>
  );
}

function currentReading(metric: LineMetric, server: ServerRecord, live: ServerInfo | undefined) {
  if (metric === "network") {
    if (connectionStatus(server) !== "online" || !live || !Number.isFinite(live.netRxBps))
      return "—";
    return `↓ ${formatRate(live.netRxBps || 0)}`;
  }
  const liveValue =
    metric === "cpu" ? live?.cpuUsage : metric === "memory" ? live?.memoryUsage : live?.diskUsage;
  const sessionValue =
    metric === "cpu"
      ? server.cpuUsage
      : metric === "memory"
        ? server.memoryUsage
        : server.diskUsage;
  const value = rowReading(server, live, liveValue, sessionValue);
  return value == null ? "—" : `${Math.round(value)}%`;
}

function busiestServer(
  servers: ServerRecord[],
  samples: Samples,
  metric: Exclude<LineMetric, "network">,
) {
  let best: { label: string; value: number } | null = null;
  for (const server of servers) {
    const live = samples.live[server.id];
    const value = rowReading(
      server,
      live,
      metric === "cpu" ? live?.cpuUsage : metric === "memory" ? live?.memoryUsage : live?.diskUsage,
      metric === "cpu"
        ? server.cpuUsage
        : metric === "memory"
          ? server.memoryUsage
          : server.diskUsage,
    );
    if (value == null) continue;
    if (!best || value > best.value) best = { label: server.name, value };
  }
  return best;
}

function ServerSwitch({
  servers,
  value,
  onChange,
}: {
  servers: ServerRecord[];
  value: string;
  onChange: (id: string) => void;
}) {
  return (
    <div
      role="tablist"
      aria-label="Server"
      className="mb-4 inline-flex max-w-full flex-wrap gap-0.5 rounded-xl border border-white/10 bg-white/5 p-0.5"
    >
      {servers.map((server) => {
        const active = server.id === value;
        return (
          <button
            key={server.id}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(server.id)}
            className={`rounded-lg px-2.5 py-1 text-[12px] font-medium transition ${
              active ? "bg-white text-zinc-900" : "text-white/70 hover:text-white"
            }`}
          >
            {server.name}
          </button>
        );
      })}
    </div>
  );
}

function MultiOverview({
  servers,
  reporting,
  combined,
  samples,
  range,
  onOpen,
}: {
  servers: ServerRecord[];
  reporting: MetricSample[];
  combined: HistoryPoint[];
  samples: Samples;
  range: RangeId;
  onOpen: (id: string) => void;
}) {
  const { servers: catalog } = useSession();
  const [chartMetric, setChartMetric] = useState<LineMetric>("cpu");
  const cpu = aggregateCpu(reporting);
  const memory = aggregateMemory(reporting);
  const disk = aggregateDisk(reporting);
  const network = aggregateNetwork(reporting);
  const online = servers.filter((server) => connectionStatus(server) === "online").length;
  const netMax = Math.max(1, ...combined.map((point) => Math.max(point.netRx, point.netTx)));
  const palette = catalog.length > 0 ? catalog : servers;
  const lines = serverLines(servers, samples, range, palette, chartMetric);

  return (
    <div className="mt-4">
      <div className="grid grid-cols-2 gap-3 xl:grid-cols-5">
        <StatCard
          icon={<Server className="size-3.5" />}
          iconClass="bg-white/10 text-white/80"
          label="Servers"
          value={`${online} / ${servers.length}`}
          detail="Online"
        >
          <span className="sr-only">Connection status is separate from the server icon.</span>
        </StatCard>
        <StatCard
          icon={<Cpu className="size-3.5" />}
          iconClass="bg-emerald-500/15 text-emerald-300"
          label="CPU"
          value={aggregatePercent(cpu.percent)}
          detail={cpuDetailLine(cpu, servers.length)}
        >
          <Sparkline points={combined} series="cpu" color="#34d399" className="h-10 w-full" />
        </StatCard>
        <StatCard
          icon={<MemoryStick className="size-3.5" />}
          iconClass="bg-fuchsia-500/15 text-fuchsia-300"
          label="Memory"
          value={aggregatePercent(memory.percent)}
          detail={byteDetail(memory, servers.length)}
        >
          <Sparkline points={combined} series="memory" color="#c084fc" className="h-10 w-full" />
        </StatCard>
        <StatCard
          icon={<HardDrive className="size-3.5" />}
          iconClass="bg-sky-500/15 text-sky-300"
          label="Disk"
          value={aggregatePercent(disk.percent)}
          detail={byteDetail(disk, servers.length)}
        >
          <Sparkline points={combined} series="disk" color="#60a5fa" className="h-10 w-full" />
        </StatCard>
        <StatCard
          icon={<Wifi className="size-3.5" />}
          iconClass="bg-pink-500/15 text-pink-300"
          label="Network"
          value={network.reporting > 0 ? `↓ ${formatRate(network.rxBps)}` : "—"}
          detail={networkDetail(network, servers.length)}
        >
          <Sparkline
            points={combined}
            series="netRx"
            color="#f472b6"
            max={netMax}
            className="h-10 w-full"
          />
        </StatCard>
      </div>

      <ServerHistory
        title="Resource Usage"
        metric={chartMetric}
        onMetric={setChartMetric}
        lines={lines}
      />

      <ServerCompareTable servers={servers} samples={samples} onOpen={onOpen} />
    </div>
  );
}

function ServerCompareTable({
  servers,
  samples,
  onOpen,
}: {
  servers: ServerRecord[];
  samples: Samples;
  onOpen: (id: string) => void;
}) {
  const [sort, setSort] = useState<CompareSort>("cpu");
  const [page, setPage] = useState(0);
  const rows = [...servers].sort((a, b) => compareRows(a, b, samples, sort));
  const pageSize = 10;
  const pageCount = Math.max(1, Math.ceil(rows.length / pageSize));
  const safePage = Math.min(page, pageCount - 1);
  const visible = rows.slice(safePage * pageSize, safePage * pageSize + pageSize);

  return (
    <section className="sui-glass-card mt-3 rounded-2xl p-4">
      <h3 className="mb-3 text-[13px] font-medium">Servers</h3>
      <div className="overflow-auto">
        <table className="w-full min-w-[760px] text-left text-[12px]">
          <thead className="text-[11px] text-white/40">
            <tr>
              <SortHeader label="Server" active={sort === "name"} onClick={() => setSort("name")} />
              <SortHeader
                label="Status"
                active={sort === "status"}
                onClick={() => setSort("status")}
              />
              <SortHeader label="CPU" active={sort === "cpu"} onClick={() => setSort("cpu")} />
              <SortHeader
                label="Memory"
                active={sort === "memory"}
                onClick={() => setSort("memory")}
              />
              <SortHeader label="Disk" active={sort === "disk"} onClick={() => setSort("disk")} />
              <SortHeader
                label="Network"
                active={sort === "network"}
                onClick={() => setSort("network")}
              />
              <SortHeader
                label="Uptime"
                active={sort === "uptime"}
                onClick={() => setSort("uptime")}
              />
              <th className="pb-2 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((server) => {
              const live = samples.live[server.id];
              const status = connectionStatus(server);
              const cpu = rowReading(server, live, live?.cpuUsage, server.cpuUsage);
              const memory = rowReading(server, live, live?.memoryUsage, server.memoryUsage);
              const disk = rowReading(server, live, live?.diskUsage, server.diskUsage);
              return (
                <tr key={server.id} className="border-t border-white/6">
                  <td className="py-2">
                    <span className="flex items-center gap-2">
                      <ServerIcon
                        iconId={server.iconId}
                        customIcon={server.customIcon}
                        serverId={server.id}
                        updatedAt={server.updatedAt}
                        size={18}
                      />
                      <span className="min-w-0">
                        <span className="block truncate font-medium">{server.name}</span>
                        <span className="block truncate text-[11px] text-white/40">
                          {server.hostname || server.address}
                        </span>
                      </span>
                    </span>
                  </td>
                  <td className="py-2 text-white/70">
                    {monitorStatusLabel(status)}
                    {server.error ? (
                      <span className="block text-[11px] text-red-300">{server.error}</span>
                    ) : null}
                    {!server.error && server.metricsError ? (
                      <span className="block text-[11px] text-white/45">{server.metricsError}</span>
                    ) : null}
                  </td>
                  <td className="py-2">{usageCell(cpu)}</td>
                  <td className="py-2">{usageCell(memory)}</td>
                  <td className="py-2">{usageCell(disk)}</td>
                  <td className="py-2 tabular-nums text-white/80">
                    {status === "online" &&
                    live &&
                    Number.isFinite(live.netRxBps) &&
                    Number.isFinite(live.netTxBps)
                      ? `↓ ${formatRate(live.netRxBps || 0)} · ↑ ${formatRate(live.netTxBps || 0)}`
                      : "—"}
                  </td>
                  <td className="py-2 text-white/70">
                    {live?.uptimeSeconds ? formatUptime(live.uptimeSeconds) : "—"}
                  </td>
                  <td className="py-2">
                    <button
                      type="button"
                      aria-label={`Open ${server.name}`}
                      onClick={() => onOpen(server.id)}
                      className="rounded-full bg-white/10 px-2.5 py-1 text-[11px] font-medium text-white hover:bg-white/16"
                    >
                      Open
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {pageCount > 1 ? (
        <div className="mt-3 flex items-center justify-end gap-2 text-[12px] text-white/60">
          <button type="button" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
            Previous
          </button>
          <span>
            {safePage + 1} / {pageCount}
          </span>
          <button
            type="button"
            disabled={safePage >= pageCount - 1}
            onClick={() => setPage(safePage + 1)}
          >
            Next
          </button>
        </div>
      ) : null}
    </section>
  );
}

type CompareSort = "name" | "status" | "cpu" | "memory" | "disk" | "network" | "uptime";

function SortHeader({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <th className="pb-2 font-medium">
      <button type="button" onClick={onClick} className={active ? "text-white" : "text-white/40"}>
        {label}
      </button>
    </th>
  );
}

function compareRows(a: ServerRecord, b: ServerRecord, samples: Samples, sort: CompareSort) {
  const rank = (server: ServerRecord) => {
    const live = samples.live[server.id];
    if (sort === "name") return server.name.toLowerCase();
    if (sort === "status") return connectionStatus(server);
    if (sort === "cpu") return rowReading(server, live, live?.cpuUsage, server.cpuUsage) ?? -1;
    if (sort === "memory")
      return rowReading(server, live, live?.memoryUsage, server.memoryUsage) ?? -1;
    if (sort === "disk") return rowReading(server, live, live?.diskUsage, server.diskUsage) ?? -1;
    if (sort === "network") {
      if (!live || !Number.isFinite(live.netRxBps) || !Number.isFinite(live.netTxBps)) return -1;
      return (live.netRxBps || 0) + (live.netTxBps || 0);
    }
    return live?.uptimeSeconds ? live.uptimeSeconds : -1;
  };
  const left = rank(a);
  const right = rank(b);
  if (typeof left === "string" && typeof right === "string")
    return right < left ? 1 : left < right ? -1 : 0;
  return Number(right) - Number(left);
}

function rowReading(
  server: ServerRecord,
  live: ServerInfo | undefined,
  liveValue: number | undefined,
  sessionValue: number | undefined,
) {
  if (connectionStatus(server) !== "online") return undefined;
  if (live && typeof liveValue === "number" && Number.isFinite(liveValue)) return liveValue;
  if (server.metricsError) return undefined;
  if (typeof sessionValue === "number" && Number.isFinite(sessionValue)) return sessionValue;
  return undefined;
}

function usageCell(value: number | undefined) {
  if (value == null || !Number.isFinite(value)) return <span className="text-white/40">—</span>;
  const high = value >= 85;
  return (
    <span className="flex items-center gap-2 tabular-nums">
      <span>
        {Math.round(value)}%{high ? " High" : ""}
      </span>
      <span className="h-1.5 w-14 overflow-hidden rounded-full bg-white/8">
        <span
          className={`block h-full rounded-full ${high ? "bg-amber-300" : "bg-emerald-400"}`}
          style={{ width: `${Math.min(100, Math.max(0, value))}%` }}
        />
      </span>
    </span>
  );
}

function aggregatePercent(value: number | null) {
  if (value == null || !Number.isFinite(value)) return "—";
  const rounded = Math.round(value * 10) / 10;
  return Number.isInteger(rounded) ? `${rounded}%` : `${rounded.toFixed(1)}%`;
}

function coverageLine(reporting: number, total: number) {
  if (reporting === total) return "";
  return `${reporting} of ${total} servers reporting`;
}

function cpuDetailLine(cpu: ReturnType<typeof aggregateCpu>, total: number) {
  const coverage = coverageLine(cpu.reporting, total);
  if (cpu.percent == null) return coverage || "No CPU capacity reported";
  return `${cpu.used.toFixed(1)} / ${cpu.total} logical CPUs${coverage ? ` · ${coverage}` : ""}`;
}

function byteDetail(value: ReturnType<typeof aggregateMemory>, total: number) {
  const coverage = coverageLine(value.reporting, total);
  if (value.percent == null) return coverage || "No byte totals reported";
  return `${formatBytes(value.used)} / ${formatBytes(value.total)}${coverage ? ` · ${coverage}` : ""}`;
}

function networkDetail(value: ReturnType<typeof aggregateNetwork>, total: number) {
  const coverage = coverageLine(value.reporting, total);
  if (value.reporting === 0) return coverage || "No throughput reported";
  return `↑ ${formatRate(value.txBps)}${coverage ? ` · ${coverage}` : ""}`;
}

function Overview({
  snap,
  online,
  chart,
  processes,
  processSort,
  onSort,
  chartsOnly = false,
}: {
  snap: ServerInfo | null;
  online: boolean;
  chart: HistoryPoint[];
  processes: MonitorProcess[];
  processSort: ProcessSort;
  onSort: (value: ProcessSort) => void;
  chartsOnly?: boolean;
}) {
  const cpu = Math.round(snap?.cpuUsage || 0);
  const mem = Math.round(snap?.memoryUsage || 0);
  const disk = Math.round(snap?.diskUsage || 0);
  return (
    <>
      <div className="mt-4 grid grid-cols-2 gap-3 xl:grid-cols-4">
        <StatCard
          icon={<Cpu className="size-3.5" />}
          iconClass="bg-emerald-500/15 text-emerald-300"
          label="CPU"
          value={online ? `${cpu}%` : "—"}
          detail={cpuDetail(snap)}
        >
          <Sparkline points={chart} series="cpu" color="#34d399" className="h-10 w-full" />
        </StatCard>
        <StatCard
          icon={<MemoryStick className="size-3.5" />}
          iconClass="bg-fuchsia-500/15 text-fuchsia-300"
          label="Memory"
          value={online ? `${mem}%` : "—"}
          detail={
            snap?.memoryTotalBytes
              ? `${formatBytes(snap.memoryUsedBytes || 0)} / ${formatBytes(snap.memoryTotalBytes)}`
              : "—"
          }
        >
          <Sparkline points={chart} series="memory" color="#c084fc" className="h-10 w-full" />
        </StatCard>
        <StatCard
          icon={<HardDrive className="size-3.5" />}
          iconClass="bg-sky-500/15 text-sky-300"
          label="Disk"
          value={online ? `${disk}%` : "—"}
          detail={
            snap?.diskTotalBytes
              ? `${formatBytes(snap.diskUsedBytes || 0)} / ${formatBytes(snap.diskTotalBytes)}`
              : "—"
          }
        >
          <Sparkline points={chart} series="disk" color="#60a5fa" className="h-10 w-full" />
        </StatCard>
        <StatCard
          icon={<Wifi className="size-3.5" />}
          iconClass="bg-pink-500/15 text-pink-300"
          label="Network"
          value={online ? `↓ ${formatRate(snap?.netRxBps || 0)}` : "—"}
          detail={online ? `↑ ${formatRate(snap?.netTxBps || 0)}` : "—"}
        >
          <Sparkline
            points={chart}
            series="netRx"
            color="#f472b6"
            max={Math.max(1, ...chart.map((p) => Math.max(p.netRx, p.netTx)))}
            className="h-10 w-full"
          />
        </StatCard>
      </div>

      <section className="mt-3 overflow-hidden sui-glass-card rounded-2xl">
        <div className="flex flex-wrap items-center justify-between gap-2 px-4 pt-4">
          <div className="flex items-center gap-2 text-[13px] font-medium">
            <HardDrive className="size-4 text-white/50" />
            Resource Usage
          </div>
          <ul className="flex flex-wrap gap-3 text-[11px] text-white/55">
            <Legend color="#34d399" label="CPU" />
            <Legend color="#c084fc" label="Memory" />
            <Legend color="#60a5fa" label="Disk" />
            <Legend color="#f472b6" label="Network" />
          </ul>
        </div>
        <div className="h-[200px] px-2 pb-2 pt-3">
          <AreaChart
            points={chart}
            series={[
              { key: "cpu", color: "#34d399" },
              { key: "memory", color: "#c084fc" },
              { key: "disk", color: "#60a5fa" },
              { key: "netRx", color: "#f472b6" },
            ]}
          />
        </div>
      </section>

      {chartsOnly ? null : (
        <div className="mt-3 grid gap-3 lg:grid-cols-[1.15fr_0.85fr]">
          <ProcessTable processes={processes} sort={processSort} onSort={onSort} />
          <SystemInfo snap={snap} />
        </div>
      )}
    </>
  );
}

function StatCard({
  icon,
  iconClass,
  label,
  value,
  detail,
  children,
}: {
  icon: ReactNode;
  iconClass: string;
  label: string;
  value: string;
  detail: string;
  children: ReactNode;
}) {
  return (
    <article className="sui-glass-card rounded-2xl p-3.5">
      <div className="flex items-center gap-2 text-[12px] text-white/55">
        <span className={`flex size-6 items-center justify-center rounded-lg ${iconClass}`}>
          {icon}
        </span>
        {label}
      </div>
      <p className="mt-2 text-[26px] font-semibold tabular-nums tracking-tight">{value}</p>
      <p className="text-[11px] text-white/40">{detail}</p>
      <div className="mt-1 overflow-hidden">{children}</div>
    </article>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <li className="flex items-center gap-1.5">
      <span className="size-2 rounded-full" style={{ background: color }} />
      {label}
    </li>
  );
}

function ProcessTable({
  processes,
  sort,
  onSort,
}: {
  processes: MonitorProcess[];
  sort: ProcessSort;
  onSort: (value: ProcessSort) => void;
}) {
  return (
    <section className="sui-glass-card rounded-2xl p-4">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2 text-[13px] font-medium">
          <List className="size-4 text-white/50" />
          Top Processes
        </div>
        <select
          aria-label="Sort processes"
          value={sort}
          onChange={(event) => onSort(event.target.value as ProcessSort)}
          className="rounded-md border border-white/10 bg-white/6 px-2 py-1 text-[11px] text-white/75 outline-none"
        >
          <option value="cpu">CPU Usage</option>
          <option value="memory">Memory</option>
        </select>
      </div>
      <div className="overflow-auto">
        <table className="w-full min-w-[420px] text-left text-[12px]">
          <thead className="text-[11px] text-white/40">
            <tr>
              <th className="pb-2 font-medium">PID</th>
              <th className="pb-2 font-medium">Name</th>
              <th className="pb-2 font-medium">CPU</th>
              <th className="pb-2 font-medium">Memory</th>
            </tr>
          </thead>
          <tbody>
            {processes.length === 0 ? (
              <tr>
                <td colSpan={4} className="py-6 text-center text-white/40">
                  No process data yet.
                </td>
              </tr>
            ) : (
              processes.map((proc) => (
                <tr key={`${proc.pid}-${proc.name}`} className="border-t border-white/6">
                  <td className="py-2 tabular-nums text-white/45">{proc.pid}</td>
                  <td className="py-2">
                    <span className="flex items-center gap-2">
                      <ProcessGlyph name={proc.name} />
                      {proc.name}
                    </span>
                  </td>
                  <td className="py-2 tabular-nums">{proc.cpu.toFixed(1)}%</td>
                  <td className="py-2">
                    <span className="flex items-center gap-2">
                      {formatBytes(proc.memoryBytes)}
                      <span className="h-1.5 w-16 overflow-hidden rounded-full bg-white/8">
                        <span
                          className="block h-full rounded-full bg-emerald-400"
                          style={{ width: `${Math.min(100, proc.memory * 4)}%` }}
                        />
                      </span>
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function SystemInfo({ snap }: { snap: ServerInfo | null }) {
  const rows = [
    ["Hostname", snap?.hostname || "—"],
    ["Operating System", snap?.osName || "—"],
    ["Kernel", snap?.kernel || "—"],
    ["Uptime", formatUptime(snap?.uptimeSeconds || 0)],
    [
      "Load Average",
      snap
        ? `${(snap.load1 || 0).toFixed(2)}, ${(snap.load5 || 0).toFixed(2)}, ${(snap.load15 || 0).toFixed(2)}`
        : "—",
    ],
    ["CPU", cpuInfo(snap)],
    ["Memory", snap?.memoryTotalBytes ? formatBytes(snap.memoryTotalBytes, 0) : "—"],
    ["Disk", snap?.diskTotalBytes ? `${formatBytes(snap.diskTotalBytes)}` : "—"],
    ["Architecture", snap?.arch || "—"],
    ["Virtualization", snap?.virtualization || "—"],
    ["IP Address", snap?.ipAddress || snap?.host || "—"],
    ["Docker Version", snap?.dockerVersion || "—"],
    ["Running Containers", snap?.containerCount != null ? String(snap.containerCount) : "—"],
  ];
  return (
    <section className="sui-glass-card rounded-2xl p-4">
      <div className="mb-3 flex items-center gap-2 text-[13px] font-medium">
        <Server className="size-4 text-white/50" />
        System Information
      </div>
      <dl className="space-y-2 text-[12px]">
        {rows.map(([label, value]) => (
          <div key={label} className="flex items-start justify-between gap-3">
            <dt className="text-white/40">{label}</dt>
            <dd className="max-w-[60%] truncate text-right text-white/85">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function ProcessGlyph({ name }: { name: string }) {
  const tone = PROCESS_COLORS[name.toLowerCase()] || "#64748b";
  return (
    <span
      className="flex size-5 items-center justify-center rounded-md text-[10px] font-semibold text-white"
      style={{ background: tone }}
    >
      {name.slice(0, 1).toUpperCase()}
    </span>
  );
}

function tabCopy(tab: Tab, online: boolean, loading: boolean, error: string | null) {
  if (!online) {
    if (loading) return "Connecting to the server…";
    return error || "Metrics unavailable.";
  }
  if (tab === "cpu") return "Detailed information about your server's CPU performance.";
  if (tab === "memory")
    return "Detailed information about your server's memory usage and statistics.";
  if (tab === "disk") return "Detailed information about your server's disk usage and performance.";
  if (tab === "network")
    return "Detailed information about your server's network usage and connections.";
  if (tab === "processes") return "View and manage running processes on your server.";
  if (tab === "services") return "Manage and monitor system services, daemons, and applications.";
  return "Real-time overview of your server's health and performance.";
}

function cpuDetail(snap: ServerInfo | null) {
  if (!snap?.cpuCores) return "—";
  const used = ((snap.cpuUsage || 0) / 100) * snap.cpuCores;
  return `${used.toFixed(1)} / ${snap.cpuCores} vCPUs`;
}

function cpuInfo(snap: ServerInfo | null) {
  if (!snap) return "—";
  if (snap.cpuModel && snap.cpuCores) return `${snap.cpuModel} (${snap.cpuCores} vCPUs)`;
  if (snap.cpuCores) return `${snap.cpuCores} vCPUs`;
  return snap.cpuModel || "—";
}

const PROCESS_COLORS: Record<string, string> = {
  node: "#3c873a",
  docker: "#2496ed",
  dockerd: "#2496ed",
  postgres: "#336791",
  ollama: "#111827",
  caddy: "#22c55e",
  nginx: "#009639",
  containerd: "#0298c3",
  redis: "#dc2626",
  "redis-server": "#dc2626",
  sshd: "#64748b",
  systemd: "#94a3b8",
};
