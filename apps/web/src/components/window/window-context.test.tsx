import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  WindowManagerProvider,
  createWindowId,
  useWindowManager,
} from "@/src/components/window/window-context";

describe("server-scoped windows", () => {
  it("gives each server its own files window and keeps terminal tabs in one window", () => {
    expect(createWindowId("files", { serverId: "prod" })).toBe("files:prod");
    expect(createWindowId("files", { serverId: "home" })).toBe("files:home");
    expect(createWindowId("terminal", { serverId: "gpu" })).toBe("terminal");
    expect(createWindowId("terminal", { serverId: "home" })).toBe("terminal");
    expect(createWindowId("editor", { serverId: "prod", filePath: "/etc/hosts" })).not.toBe(
      createWindowId("editor", { serverId: "home", filePath: "/etc/hosts" }),
    );
  });

  it("keeps two file windows when they target different servers", async () => {
    const user = userEvent.setup();

    function Harness() {
      const { windows, openWindow } = useWindowManager();
      return (
        <>
          <button
            type="button"
            onClick={() => openWindow("files", { serverId: "prod", serverName: "Production" })}
          >
            Production
          </button>
          <button
            type="button"
            onClick={() => openWindow("files", { serverId: "home", serverName: "Homelab" })}
          >
            Homelab
          </button>
          <ul>
            {windows.map((item) => (
              <li key={item.id}>
                {item.id}:{item.serverId}
              </li>
            ))}
          </ul>
        </>
      );
    }

    render(
      <WindowManagerProvider>
        <Harness />
      </WindowManagerProvider>,
    );

    await user.click(screen.getByRole("button", { name: "Production" }));
    await user.click(screen.getByRole("button", { name: "Homelab" }));

    expect(screen.getByText("files:prod:prod")).toBeInTheDocument();
    expect(screen.getByText("files:home:home")).toBeInTheDocument();
  });
});
