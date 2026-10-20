"use client";

import { ServerIcon } from "@/src/components/servers/ServerIcon";
import { connectionStatus, type Server } from "@/src/lib/servers";
import { ServerStatus } from "@/src/components/server-selection/ServerStatus";

function pct(value: number | undefined) {
  return typeof value === "number" ? `${Math.round(value)}%` : "—";
}

export function ServerCard({
  server,
  testing,
  testMessage,
  onConnect,
  onDisconnect,
  onReconnect,
  onOpenFiles,
  onOpenTerminal,
  onOpenMetrics,
  onTest,
  onEdit,
  onDelete,
}: {
  server: Server;
  testing?: boolean;
  testMessage?: string | null;
  onConnect: () => void;
  onDisconnect?: () => void;
  onReconnect?: () => void;
  onOpenFiles?: () => void;
  onOpenTerminal?: () => void;
  onOpenMetrics?: () => void;
  onTest: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const status = connectionStatus(server);
  const usage =
    status === "online"
      ? [
          server.metricsError ? null : `CPU ${pct(server.cpuUsage)}`,
          server.metricsError ? null : `Memory ${pct(server.memoryUsage)}`,
          server.metricsError ? null : `Disk ${pct(server.diskUsage)}`,
          typeof server.latencyMs === "number" ? `${server.latencyMs} ms` : null,
        ]
          .filter(Boolean)
          .join(" · ")
      : "";
  return (
    <div className="sui-card animate-card-in rounded-[22px] p-5 shadow-[0_18px_50px_rgba(0,0,0,0.28)]">
      <div className="relative flex min-h-[118px] flex-col items-start text-left">
        <span
          className={`absolute right-0 top-0 size-2 rounded-full ${
            status === "online"
              ? "bg-emerald-400 shadow-[0_0_10px_rgba(52,211,153,0.85)]"
              : status === "connecting" || status === "reconnecting"
                ? "bg-amber-300"
                : status === "authentication_failed" || status === "error"
                  ? "bg-red-400"
                  : "bg-zinc-400"
          }`}
          aria-hidden
        />
        <span className="mb-3 flex size-10 items-center justify-center">
          <ServerIcon
            iconId={server.iconId}
            customIcon={server.customIcon}
            serverId={server.id}
            updatedAt={server.updatedAt}
            size={36}
          />
        </span>
        <p className="pr-6 text-[17px] font-semibold tracking-tight text-white">{server.name}</p>
        {server.hostname && server.hostname !== server.name ? (
          <p className="mt-3 font-mono text-[12px] text-white/58">{server.hostname}</p>
        ) : null}
        <p
          className={`${server.hostname && server.hostname !== server.name ? "mt-1" : "mt-3"} font-mono text-[12px] text-white/58`}
        >
          {server.username ? `${server.username}@${server.address}` : server.address}
          {server.sshPort && server.sshPort !== 22 ? `:${server.sshPort}` : ""}
        </p>
        <div className="mt-auto pt-5">
          <ServerStatus status={status} />
        </div>
      </div>

      {usage ? <p className="mt-3 text-[12px] text-white/55">{usage}</p> : null}
      {server.metricsError && status === "online" ? (
        <p className="mt-3 text-[12px] text-white/55">{server.metricsError}</p>
      ) : null}
      {server.error && status !== "online" && status !== "connecting" ? (
        <p className="mt-3 text-[12px] text-red-300/90">{server.error}</p>
      ) : null}

      {testMessage ? (
        <p className="mt-3 text-[12px] text-white/70" role="status">
          {testMessage}
        </p>
      ) : null}

      <div className="mt-4 flex flex-wrap gap-2">
        {status === "online" ? null : (
          <button
            type="button"
            onClick={onConnect}
            disabled={status === "connecting" || status === "reconnecting"}
            className="rounded-full bg-white px-3 py-1.5 text-[12px] font-semibold text-zinc-900 transition hover:bg-white/90 disabled:opacity-60"
          >
            {status === "connecting" || status === "reconnecting" ? "Connecting…" : "Connect"}
          </button>
        )}
        <button
          type="button"
          onClick={onTest}
          disabled={testing}
          className="rounded-full bg-white/12 px-3 py-1.5 text-[12px] font-medium text-white transition hover:bg-white/18 disabled:opacity-60"
        >
          {testing ? "Testing…" : "Test Connection"}
        </button>
        {status === "online" && onDisconnect ? (
          <button
            type="button"
            onClick={onDisconnect}
            className="rounded-full bg-white/12 px-3 py-1.5 text-[12px] font-medium text-white transition hover:bg-white/18"
          >
            Disconnect
          </button>
        ) : null}
        {onReconnect && status !== "connecting" && status !== "reconnecting" ? (
          <button
            type="button"
            onClick={onReconnect}
            className="rounded-full bg-white/12 px-3 py-1.5 text-[12px] font-medium text-white transition hover:bg-white/18"
          >
            Reconnect
          </button>
        ) : null}
        {onOpenFiles ? (
          <button
            type="button"
            onClick={onOpenFiles}
            className="rounded-full px-3 py-1.5 text-[12px] font-medium text-white/78 transition hover:bg-white/10 hover:text-white"
          >
            Open Files
          </button>
        ) : null}
        {onOpenTerminal ? (
          <button
            type="button"
            onClick={onOpenTerminal}
            className="rounded-full px-3 py-1.5 text-[12px] font-medium text-white/78 transition hover:bg-white/10 hover:text-white"
          >
            Open Terminal
          </button>
        ) : null}
        {onOpenMetrics ? (
          <button
            type="button"
            onClick={onOpenMetrics}
            className="rounded-full px-3 py-1.5 text-[12px] font-medium text-white/78 transition hover:bg-white/10 hover:text-white"
          >
            View Metrics
          </button>
        ) : null}
        <button
          type="button"
          onClick={onEdit}
          className="rounded-full px-3 py-1.5 text-[12px] font-medium text-white/78 transition hover:bg-white/10 hover:text-white"
        >
          Edit
        </button>
        <button
          type="button"
          onClick={onDelete}
          className="rounded-full px-3 py-1.5 text-[12px] font-medium text-red-300/90 transition hover:bg-red-400/10 hover:text-red-200"
        >
          Delete
        </button>
      </div>
    </div>
  );
}
