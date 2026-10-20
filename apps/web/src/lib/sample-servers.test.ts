import { describe, expect, it } from "vitest";
import { mergeSampleServers, previewMetrics, sampleMetrics } from "@/src/lib/sample-servers";
import type { Server } from "@/src/lib/servers";

const real: Server = {
  id: "srv_real",
  name: "Edge",
  hostname: "edge",
  address: "203.0.113.50",
  status: "online",
  connected: true,
  username: "deploy",
};

describe("sample servers", () => {
  it("leaves the inventory unchanged outside development", () => {
    expect(mergeSampleServers([real])).toEqual([real]);
    expect(sampleMetrics("sample_northwind")).toBeNull();
  });

  it("adds three connected preview hosts and one that stays offline", () => {
    const merged = mergeSampleServers([real], true);
    expect(merged[0]).toBe(real);
    expect(merged.map((item) => item.name)).toEqual([
      "Edge",
      "Sample Northwind",
      "Sample Lakeside",
      "Sample Harbor",
      "Sample Relay",
    ]);
    expect(merged.filter((item) => item.connected)).toHaveLength(4);
    expect(merged.find((item) => item.id === "sample_relay")?.error).toBe("authentication failed");
  });

  it("builds distinct metric history for each connected preview host", () => {
    const now = Date.parse("2026-10-15T06:00:00Z");
    const north = previewMetrics("sample_northwind", now);
    const harbor = previewMetrics("sample_harbor", now);
    expect(north?.history).toHaveLength(60);
    expect(harbor?.history).toHaveLength(60);
    expect(north?.cpuUsage).not.toBe(harbor?.cpuUsage);
    expect(north?.host).toBe("203.0.113.10");
    expect(north?.netRxBps || 0).toBeGreaterThan(100_000);
    expect(harbor?.processes?.[0]?.name).toBe("node");
  });
});
