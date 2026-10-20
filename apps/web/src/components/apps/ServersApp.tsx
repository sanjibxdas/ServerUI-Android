"use client";

import { ServerManager } from "@/src/components/apps/servers/ServerManager";
import { useWindowManager } from "@/src/components/window/window-context";
import { useSession } from "@/src/lib/session";
import type { Server } from "@/src/lib/servers";

export function ServersApp() {
  const { openWindow } = useWindowManager();
  const { connectServer, disconnectServer, reconnectServer } = useSession();

  function target(server: Server) {
    return { serverId: server.id, serverName: server.name };
  }

  return (
    <ServerManager
      actions={{
        connect: (server) => {
          void connectServer(server.id).catch(() => {});
        },
        disconnect: (server) => {
          void disconnectServer(server.id).catch(() => {});
        },
        reconnect: (server) => {
          void reconnectServer(server.id).catch(() => {});
        },
        openFiles: (server) => openWindow("files", target(server)),
        openTerminal: (server) => openWindow("terminal", target(server)),
        openMetrics: (server) => {
          if (!server.connected) void connectServer(server.id).catch(() => {});
          openWindow("dashboard", target(server));
        },
      }}
    />
  );
}
