"use client";

import { useEffect, useState, type MouseEvent } from "react";
import { Moon, SlidersHorizontal, Sun } from "lucide-react";
import { motion } from "framer-motion";

import { AppearanceSettings } from "@/src/components/shell/AppearanceSettings";
import { BrandMark } from "@/src/components/brand/BrandMark";
import { DesktopShortcuts } from "@/src/components/desktop/DesktopShortcuts";
import { WindowManager } from "@/src/components/window/WindowManager";
import { useWindowManager } from "@/src/components/window/window-context";
import { APP_META, visibleDockOrder, type AppId, type DockAppId } from "@/src/data/apps";
import { APP_ICONS } from "@/src/lib/app-icons";
import { useAppearance } from "@/src/lib/appearance";
import { formatDockBadge, useDockActivity } from "@/src/lib/dock-activity";
import { useDockPrefs } from "@/src/lib/dock-prefs";
import { useTheme } from "@/src/lib/theme";
import { WallpaperBackdrop } from "@/src/lib/wallpaper";

type Panel = "launcher" | "settings" | null;

export function AuraDesktop() {
  const { windows, focusedId, clearFocus, closeWindow, openWindow, toggleDockApp } =
    useWindowManager();
  const { theme, toggleTheme } = useTheme();
  const { prefs } = useAppearance();
  const { order, hideMode } = useDockPrefs();
  const activity = useDockActivity();
  const [panel, setPanel] = useState<Panel>(null);
  const [clock, setClock] = useState("");
  const apps: AppId[] = [...visibleDockOrder(order), "about"];

  useEffect(() => {
    const tick = () =>
      setClock(
        new Date().toLocaleTimeString([], {
          hour: "2-digit",
          minute: "2-digit",
          hour12: prefs.clock12,
        }),
      );
    tick();
    const id = window.setInterval(tick, 15000);
    return () => window.clearInterval(id);
  }, [prefs.clock12]);

  useEffect(() => {
    function isTypingTarget(target: EventTarget | null) {
      if (!(target instanceof HTMLElement)) return false;
      if (target.closest(".xterm") || target.closest(".xterm-helper-textarea")) return true;
      const tag = target.tagName;
      return tag === "INPUT" || tag === "TEXTAREA" || target.isContentEditable;
    }

    function onKey(event: KeyboardEvent) {
      const meta = event.metaKey || event.ctrlKey;
      if (event.key === "Escape") {
        setPanel(null);
        return;
      }
      if (!meta) return;
      if (event.key.toLowerCase() === "k") {
        event.preventDefault();
        setPanel((current) => (current === "launcher" ? null : "launcher"));
      }
      if (event.key.toLowerCase() === ",") {
        event.preventDefault();
        openWindow("settings");
      }
      if (event.key.toLowerCase() === "w") {
        if (isTypingTarget(event.target) || !focusedId) return;
        event.preventDefault();
        closeWindow(focusedId);
      }
    }

    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [closeWindow, focusedId, openWindow]);

  function toggle(next: Panel) {
    setPanel((current) => (current === next ? null : next));
  }

  function onPointerDown(event: MouseEvent<HTMLElement>) {
    if (!(event.target instanceof Element)) return;
    if (event.target.closest('[role="dialog"],[role="menu"],button,nav')) return;
    clearFocus();
    setPanel(null);
  }

  return (
    <div className="shell-desktop" data-right={prefs.right}>
      <aside className="shell-rail" aria-label="Aura">
        <nav
          className="shell-apps shell-pill"
          aria-label="Applications"
          data-autohide={hideMode === "auto" ? "true" : "false"}
        >
          {apps.map((id) => {
            const Icon = APP_ICONS[id];
            const running = windows.some((item) => item.app === id);
            const current = windows.some(
              (item) => item.app === id && item.id === focusedId && !item.minimized,
            );
            const badge = formatDockBadge(activity.badges[id as DockAppId] || 0);
            return (
              <motion.button
                key={id}
                type="button"
                className="shell-icon shell-app-icon"
                aria-label={id === "about" ? "Info" : APP_META[id].title}
                title={APP_META[id].title}
                aria-pressed={running}
                aria-current={current ? "page" : undefined}
                onClick={() => toggleDockApp(id)}
                whileHover={{ scale: 1.12, y: -2 }}
                whileTap={{ scale: 0.94 }}
                transition={{ type: "spring", stiffness: 450, damping: 25, mass: 0.8 }}
              >
                <Icon size={21} strokeWidth={1.7} aria-hidden />
                {running ? <span className="shell-running" /> : null}
                {badge ? <span className="shell-badge">{badge}</span> : null}
              </motion.button>
            );
          })}
        </nav>
        <div className="shell-rail-spacer" />
        <div className="shell-pill shell-utilities">
          <motion.button
            type="button"
            className="shell-icon"
            aria-label={theme === "dark" ? "Light mode" : "Dark mode"}
            title={theme === "dark" ? "Light mode" : "Dark mode"}
            aria-pressed={theme === "light"}
            onClick={toggleTheme}
            whileHover={{ scale: 1.12, y: -2 }}
            whileTap={{ scale: 0.94 }}
            transition={{ type: "spring", stiffness: 450, damping: 25, mass: 0.8 }}
          >
            {theme === "dark" ? <Sun size={20} /> : <Moon size={20} />}
          </motion.button>
          <motion.button
            type="button"
            className="shell-icon shell-quick-settings"
            aria-label="Quick settings"
            onClick={() => toggle("settings")}
            whileHover={{ scale: 1.12, y: -2 }}
            whileTap={{ scale: 0.94 }}
            transition={{ type: "spring", stiffness: 450, damping: 25, mass: 0.8 }}
          >
            <SlidersHorizontal size={20} />
          </motion.button>
        </div>
      </aside>
      <main className="shell-screen" onPointerDown={onPointerDown}>
        <WallpaperBackdrop className="pointer-events-none absolute inset-0 bg-cover bg-center opacity-80" />
        <div className="shell-hero">
          <BrandMark size={46} />
          <h1>ServerUI</h1>
          <p>
            Your servers.
            <br />
            Your workspace.
          </p>
        </div>
        <div className="shell-desktop-caption">
          Control
          <br />
          Deploy
          <br />
          Monitor
        </div>
        <time className="shell-desktop-clock">{clock}</time>
        <div className="shell-workspace" role="application" aria-label="Server desktop">
          <DesktopShortcuts serverId="serverui" />
          <WindowManager />
        </div>
        {panel === "launcher" ? (
          <Launcher
            apps={apps}
            onOpen={(id) => {
              toggleDockApp(id);
              setPanel(null);
            }}
            onClose={() => setPanel(null)}
          />
        ) : null}
        {panel === "settings" ? (
          <div className="shell-panel-overlay" onClick={() => setPanel(null)}>
            <div
              className="shell-panel shell-panel-settings"
              role="dialog"
              aria-label="Quick settings"
              onClick={(event) => event.stopPropagation()}
            >
              <AppearanceSettings />
            </div>
          </div>
        ) : null}
      </main>
    </div>
  );
}

function Launcher({
  apps,
  onOpen,
  onClose,
}: {
  apps: AppId[];
  onOpen: (id: AppId) => void;
  onClose: () => void;
}) {
  return (
    <div className="shell-panel-overlay" onClick={onClose}>
      <div
        className="shell-panel shell-panel-launcher"
        role="dialog"
        aria-label="Launcher"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="shell-panel-title">
          <h2>Applications</h2>
        </div>
        <div className="shell-launcher-results">
          {apps.map((id) => {
            const Icon = APP_ICONS[id];
            const title = id === "about" ? "Info" : APP_META[id].title;
            return (
              <button key={id} type="button" className="shell-list-item" onClick={() => onOpen(id)}>
                <Icon size={18} aria-hidden />
                <span>
                  <b>{title}</b>
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
