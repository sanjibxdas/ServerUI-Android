import type { ServerStatus as Status } from "@/src/lib/servers";

export function ServerStatus({ status }: { status: Status }) {
  const label =
    status === "online"
      ? "Connected"
      : status === "connecting"
        ? "Connecting"
        : status === "reconnecting"
          ? "Reconnecting"
          : status === "authentication_failed"
            ? "Authentication failed"
            : status === "error"
              ? "Error"
              : status === "unknown"
                ? "Unknown"
                : "Disconnected";
  const dot =
    status === "online"
      ? "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.9)]"
      : status === "connecting" || status === "reconnecting"
        ? "bg-amber-300"
        : status === "authentication_failed" || status === "error"
          ? "bg-red-400"
          : "bg-zinc-400";

  return (
    <span className="inline-flex items-center gap-2 text-[13px] text-white/78">
      <span className={`size-1.5 rounded-full ${dot}`} aria-hidden />
      <span>{label}</span>
    </span>
  );
}
