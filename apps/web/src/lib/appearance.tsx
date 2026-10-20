"use client";

import { useEffect, useMemo, useSyncExternalStore } from "react";

export type AppearanceId = "macos" | "aura";

const APPEARANCE_KEY = "serverui-appearance";
const PREFS_KEY = "serverui-shell";
const EVENT = "serverui-appearance";

export const ACCENT_PRESETS = {
  Lavender: "#d0bcff",
  Purple: "#e2b0ff",
  Blue: "#a8c7fa",
  Cyan: "#7fd9e0",
  Rose: "#ffb1c8",
} as const;

export const SHELL_DEFAULTS = {
  accent: "#d0bcff",
  blur: 14,
  opacity: 94,
  radius: 18,
  right: false,
  compact: false,
  reduced: false,
  clock12: false,
};

export type ShellPrefs = typeof SHELL_DEFAULTS;

function emit() {
  window.dispatchEvent(new Event(EVENT));
}

function subscribe(onStoreChange: () => void) {
  window.addEventListener(EVENT, onStoreChange);
  window.addEventListener("storage", onStoreChange);
  return () => {
    window.removeEventListener(EVENT, onStoreChange);
    window.removeEventListener("storage", onStoreChange);
  };
}

function readAppearance(): AppearanceId {
  try {
    return localStorage.getItem(APPEARANCE_KEY) === "aura" ? "aura" : "macos";
  } catch {
    return "macos";
  }
}

function readPrefsRaw() {
  try {
    return localStorage.getItem(PREFS_KEY) || "";
  } catch {
    return "";
  }
}

export function parseShellPrefs(raw: string): ShellPrefs {
  const next = { ...SHELL_DEFAULTS };
  try {
    const parsed = JSON.parse(raw) as Partial<ShellPrefs>;
    if (typeof parsed.accent === "string" && /^#[\da-f]{6}$/i.test(parsed.accent)) {
      next.accent = parsed.accent;
    }
    for (const key of ["right", "compact", "reduced", "clock12"] as const) {
      if (typeof parsed[key] === "boolean") next[key] = parsed[key];
    }
    const ranges = { blur: [0, 30], opacity: [60, 100], radius: [6, 32] } as const;
    for (const key of ["blur", "opacity", "radius"] as const) {
      const value = parsed[key];
      if (typeof value === "number" && Number.isFinite(value)) {
        const [min, max] = ranges[key];
        next[key] = Math.min(max, Math.max(min, value));
      }
    }
  } catch {
    return SHELL_DEFAULTS;
  }
  return next;
}

export function useAppearance() {
  const appearance = useSyncExternalStore(subscribe, readAppearance, () => "macos" as AppearanceId);
  const raw = useSyncExternalStore(subscribe, readPrefsRaw, () => "");
  const prefs = useMemo(() => parseShellPrefs(raw), [raw]);

  function setAppearance(next: AppearanceId) {
    try {
      localStorage.setItem(APPEARANCE_KEY, next);
    } catch {
      // Ignore private-mode storage failures.
    }
    emit();
  }

  function updatePrefs(patch: Partial<ShellPrefs>) {
    try {
      localStorage.setItem(
        PREFS_KEY,
        JSON.stringify({ ...parseShellPrefs(readPrefsRaw()), ...patch }),
      );
    } catch {
      // Ignore private-mode storage failures.
    }
    emit();
  }

  return { appearance, setAppearance, prefs, updatePrefs };
}

/** Applies the Aura shell tokens. macOS light and dark stay on the existing theme toggle. */
export function AppearanceEffects() {
  const { appearance, prefs } = useAppearance();
  useEffect(() => {
    const root = document.documentElement;
    if (appearance !== "aura") {
      delete root.dataset.appearance;
      delete root.dataset.compact;
      delete root.dataset.motion;
      for (const name of ["--shell-accent", "--shell-blur", "--shell-opacity", "--shell-radius"]) {
        root.style.removeProperty(name);
      }
      return;
    }
    root.dataset.appearance = "aura";
    root.dataset.compact = String(prefs.compact);
    root.dataset.motion = prefs.reduced ? "reduced" : "system";
    root.style.setProperty("--shell-accent", prefs.accent);
    root.style.setProperty("--shell-blur", `${prefs.blur}px`);
    root.style.setProperty("--shell-opacity", `${prefs.opacity}%`);
    root.style.setProperty("--shell-radius", `${prefs.radius}px`);
  }, [appearance, prefs]);
  return null;
}
