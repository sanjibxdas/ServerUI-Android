import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DashboardApp } from "@/src/components/apps/DashboardApp";
import { WindowManagerProvider } from "@/src/components/window/window-context";

const getServerMetrics = vi.fn();

vi.mock("@/src/lib/api/server", () => ({
  getServerMetrics: (...args: unknown[]) => getServerMetrics(...args),
}));

vi.mock("@/src/lib/session", () => ({
  useSelectedServer: () => null,
  useSession: () => ({
    servers: [
      {
        id: "prod",
        name: "Production",
        hostname: "prod",
        address: "203.0.113.10",
        username: "deploy",
        status: "online",
        connected: true,
        cpuUsage: 20,
        memoryUsage: 30,
        diskUsage: 40,
      },
      {
        id: "home",
        name: "Homelab",
        hostname: "home",
        address: "203.0.113.20",
        username: "deploy",
        status: "offline",
        connected: false,
        error: "authentication failed",
      },
      {
        id: "gpu",
        name: "GPU Server",
        hostname: "gpu",
        address: "203.0.113.30",
        username: "deploy",
        status: "online",
        connected: true,
        metricsError: "Metrics unavailable",
      },
    ],
    loadingServers: false,
    serversError: null,
    connectServer: vi.fn(),
  }),
}));

function renderMonitor() {
  return render(
    <WindowManagerProvider>
      <DashboardApp />
    </WindowManagerProvider>,
  );
}

const gb = 1024 ** 3;

describe("system monitor", () => {
  beforeEach(() => {
    getServerMetrics.mockReset();
    getServerMetrics.mockResolvedValue(undefined);
  });

  it("lists every selected server and does not poll a disconnected one", () => {
    renderMonitor();

    expect(screen.getByRole("button", { name: "All servers (3)" })).toBeInTheDocument();
    expect(screen.getAllByText("Production").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Homelab").length).toBeGreaterThan(0);
    expect(screen.getByText("2 / 3")).toBeInTheDocument();
    expect(screen.getByText("20%")).toBeInTheDocument();
    expect(screen.getByText("Metrics unavailable")).toBeInTheDocument();
    expect(screen.getAllByText("0 of 3 servers reporting").length).toBeGreaterThan(0);
    expect(getServerMetrics).toHaveBeenCalledWith("prod");
    expect(getServerMetrics).toHaveBeenCalledWith("gpu");
    expect(getServerMetrics).not.toHaveBeenCalledWith("home");

    fireEvent.click(screen.getByRole("button", { name: "All servers (3)" }));
    expect(screen.getByRole("menuitemcheckbox", { name: /Homelab/ })).toBeEnabled();
    expect(screen.getAllByText(/authentication failed/).length).toBeGreaterThan(0);
    expect(screen.getByRole("menuitemcheckbox", { name: /Production/ })).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("sorts the comparison table without changing the selected servers", () => {
    renderMonitor();
    fireEvent.click(screen.getByRole("button", { name: "Server" }));

    expect(screen.getByRole("button", { name: "All servers (3)" })).toBeInTheDocument();
    expect(screen.getAllByText("Production").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Homelab").length).toBeGreaterThan(0);
    expect(screen.getAllByText("GPU Server").length).toBeGreaterThan(0);
  });

  it("opens one server without removing the others from the desktop", () => {
    renderMonitor();
    fireEvent.click(screen.getByRole("button", { name: "Open Production" }));

    expect(screen.getByRole("heading", { name: "System Overview" })).toBeInTheDocument();
    expect(screen.getByText("Top Processes")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Production" })).toBeInTheDocument();
    expect(screen.queryByText("Homelab")).not.toBeInTheDocument();
  });

  it("weights CPU and memory across servers that report capacity", async () => {
    getServerMetrics.mockImplementation(async (id: string) => {
      if (id === "prod") {
        return {
          id: "prod",
          name: "Production",
          hostname: "prod",
          status: "online",
          host: "203.0.113.10",
          username: "deploy",
          cpuUsage: 70,
          cpuCores: 8,
          memoryUsedBytes: 6 * gb,
          memoryTotalBytes: 8 * gb,
          diskUsedBytes: 100,
          diskTotalBytes: 200,
          netRxBps: 1_000,
          netTxBps: 400,
          uptimeSeconds: 3600,
          history: [],
        };
      }
      if (id === "gpu") {
        return {
          id: "gpu",
          name: "GPU Server",
          hostname: "gpu",
          status: "online",
          host: "203.0.113.30",
          username: "deploy",
          cpuUsage: 40,
          cpuCores: 16,
          memoryUsedBytes: 24 * gb,
          memoryTotalBytes: 32 * gb,
          diskUsedBytes: 50,
          diskTotalBytes: 100,
          netRxBps: 2_000,
          netTxBps: 100,
          uptimeSeconds: 7200,
          history: [],
        };
      }
      return undefined;
    });

    renderMonitor();

    expect(
      await screen.findByText(/12\.0 \/ 24 logical CPUs · 2 of 3 servers reporting/),
    ).toBeInTheDocument();
    expect(screen.getAllByText("75%").length).toBeGreaterThan(0);
    expect(screen.getAllByText("50%").length).toBeGreaterThan(0);
    expect(screen.getByText(/30\.0 GB \/ 40\.0 GB/)).toBeInTheDocument();
    expect(screen.getAllByText("Homelab").length).toBeGreaterThan(0);
  });
});
