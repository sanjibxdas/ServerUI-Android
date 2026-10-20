import { describe, expect, it } from "vitest";
import { listPreviewFiles, transferPreviewFile } from "@/src/lib/preview-files";

describe("preview files", () => {
  it("lists a home folder for a preview server", () => {
    const home = listPreviewFiles("sample_northwind", "~");
    expect(home.path).toBe("/home/deploy");
    expect(home.entries.map((entry) => entry.name)).toContain("northwind.txt");
  });

  it("copies a file from one preview server into another", () => {
    transferPreviewFile(
      "sample_northwind",
      "sample_harbor",
      "/home/deploy/northwind.txt",
      "/tmp",
      false,
      false,
    );
    const tmp = listPreviewFiles("sample_harbor", "/tmp");
    expect(tmp.entries.map((entry) => entry.name)).toContain("northwind.txt");
    expect(
      listPreviewFiles("sample_northwind", "/home/deploy").entries.map((entry) => entry.name),
    ).toContain("northwind.txt");
  });
});
