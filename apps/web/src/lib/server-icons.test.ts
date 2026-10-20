import { describe, expect, it } from "vitest";
import {
  DEFAULT_SERVER_ICON,
  SERVER_ICONS,
  isServerIconId,
  serverIcon,
} from "@/src/lib/server-icons";

describe("server icon registry", () => {
  it("has 16 unique labeled presets and a default", () => {
    const ids = SERVER_ICONS.map((item) => item.id);
    expect(new Set(ids).size).toBe(16);
    expect(ids).toContain(DEFAULT_SERVER_ICON);
    for (const item of SERVER_ICONS) {
      expect(item.label.trim().length).toBeGreaterThan(0);
      expect(item.Icon).toBeTruthy();
      expect(serverIcon(item.id).id).toBe(item.id);
      expect(isServerIconId(item.id)).toBe(true);
    }
  });

  it("rejects unknown ids by falling back to the default preset", () => {
    expect(isServerIconId("nope")).toBe(false);
    expect(serverIcon(undefined).id).toBe(DEFAULT_SERVER_ICON);
    expect(serverIcon("nope").id).toBe(DEFAULT_SERVER_ICON);
  });
});
