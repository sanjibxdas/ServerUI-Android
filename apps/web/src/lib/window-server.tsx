"use client";

import { createContext, useContext, type ReactNode } from "react";

// undefined: this tree is outside a window, so callers may use session state.
// null: this window is not aimed at a server.
// string: this window is aimed at that server id.
const WindowServerContext = createContext<string | null | undefined>(undefined);

export function WindowServerProvider({
  serverId,
  children,
}: {
  serverId?: string | null;
  children: ReactNode;
}) {
  return (
    <WindowServerContext.Provider value={serverId || null}>{children}</WindowServerContext.Provider>
  );
}

export function useWindowServerId() {
  return useContext(WindowServerContext);
}
