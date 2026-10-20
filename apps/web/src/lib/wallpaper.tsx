"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";

import type { Theme } from "@/src/lib/theme";

export type BuiltInWallpaperId = "crescent" | "valley" | "orbit";
export type WallpaperId = BuiltInWallpaperId | "custom";

export type WallpaperOption = {
  id: BuiltInWallpaperId;
  name: string;
  src: string;
};

const DARK_WALLPAPERS: readonly WallpaperOption[] = [
  {
    id: "crescent",
    name: "Crescent",
    src: "/wallpapers/crescent.png",
  },
  {
    id: "valley",
    name: "Valley",
    src: "/wallpapers/valley.png",
  },
  {
    id: "orbit",
    name: "Orbit",
    src: "/wallpapers/orbit.png",
  },
];

const LIGHT_WALLPAPERS: readonly WallpaperOption[] = [
  {
    id: "valley",
    name: "Sunrise",
    src: "/wallpapers/sunrise.png",
  },
  {
    id: "crescent",
    name: "Fjord",
    src: "/wallpapers/fjord.png",
  },
  {
    id: "orbit",
    name: "Alpine",
    src: "/wallpapers/alpine.png",
  },
];

const WALLPAPERS: readonly WallpaperOption[] = DARK_WALLPAPERS;

export function wallpapersFor(theme: Theme): readonly WallpaperOption[] {
  return theme === "light" ? LIGHT_WALLPAPERS : DARK_WALLPAPERS;
}

const THEME_EVENT = "serverui-theme";

function readTheme(): Theme {
  if (typeof document === "undefined") return "dark";
  return document.documentElement.dataset.theme === "light" ? "light" : "dark";
}

function subscribeTheme(onStoreChange: () => void) {
  window.addEventListener(THEME_EVENT, onStoreChange);
  window.addEventListener("storage", onStoreChange);
  return () => {
    window.removeEventListener(THEME_EVENT, onStoreChange);
    window.removeEventListener("storage", onStoreChange);
  };
}

const DEFAULT_WALLPAPER_ID: BuiltInWallpaperId = "crescent";
const LIGHT_DEFAULT_WALLPAPER_ID: BuiltInWallpaperId = "valley";

const STORAGE_KEY = "serverui-wallpaper";
const LIGHT_STORAGE_KEY = "serverui-wallpaper-light";
const CUSTOM_EVENT = "serverui-wallpaper";
const MAX_CUSTOM_BYTES = 8 * 1024 * 1024;
const DB_NAME = "serverui";
const DB_STORE = "prefs";
const CUSTOM_KEY = "wallpaper-custom";

type WallpaperContextValue = {
  wallpaperId: WallpaperId;
  wallpaperSrc: string;
  wallpapers: readonly WallpaperOption[];
  customSrc: string | null;
  hasCustom: boolean;
  setWallpaper: (id: WallpaperId) => void;
  setCustomWallpaper: (file: File) => Promise<void>;
  clearCustomWallpaper: () => Promise<void>;
};

const WallpaperContext = createContext<WallpaperContextValue | null>(null);

function isWallpaperId(value: string | null): value is WallpaperId {
  return value === "custom" || WALLPAPERS.some((item) => item.id === value);
}

function defaultWallpaperId(theme: Theme): BuiltInWallpaperId {
  return theme === "light" ? LIGHT_DEFAULT_WALLPAPER_ID : DEFAULT_WALLPAPER_ID;
}

function storageKey(theme: Theme) {
  return theme === "light" ? LIGHT_STORAGE_KEY : STORAGE_KEY;
}

function readStoredId(theme: Theme = readTheme()): WallpaperId {
  try {
    const value = localStorage.getItem(storageKey(theme));
    if (isWallpaperId(value)) return value;
  } catch {
    // Ignore private-mode storage failures.
  }
  return defaultWallpaperId(theme);
}

function writeStoredId(id: WallpaperId, theme: Theme = readTheme()) {
  try {
    localStorage.setItem(storageKey(theme), id);
  } catch {
    // Ignore private-mode storage failures.
  }
  window.dispatchEvent(new Event(CUSTOM_EVENT));
}

function builtInSrc(id: WallpaperId, theme: Theme): string {
  const options = wallpapersFor(theme);
  if (id === "custom") return options[0].src;
  return options.find((item) => item.id === id)?.src ?? options[0].src;
}

function openPrefsDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(DB_STORE)) {
        db.createObjectStore(DB_STORE);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Unable to open wallpaper storage"));
  });
}

async function saveCustomBlob(blob: Blob) {
  const db = await openPrefsDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(DB_STORE, "readwrite");
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error("Unable to save wallpaper"));
    tx.objectStore(DB_STORE).put(blob, CUSTOM_KEY);
  });
  db.close();
}

async function loadCustomBlob(): Promise<Blob | null> {
  const db = await openPrefsDb();
  const blob = await new Promise<Blob | null>((resolve, reject) => {
    const tx = db.transaction(DB_STORE, "readonly");
    const request = tx.objectStore(DB_STORE).get(CUSTOM_KEY);
    request.onsuccess = () => {
      const value = request.result;
      resolve(value instanceof Blob ? value : null);
    };
    request.onerror = () => reject(request.error ?? new Error("Unable to load wallpaper"));
  });
  db.close();
  return blob;
}

async function deleteCustomBlob() {
  const db = await openPrefsDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(DB_STORE, "readwrite");
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error("Unable to remove wallpaper"));
    tx.objectStore(DB_STORE).delete(CUSTOM_KEY);
  });
  db.close();
}

export function WallpaperProvider({ children }: { children: ReactNode }) {
  const theme = useSyncExternalStore(subscribeTheme, readTheme, () => "dark" as Theme);
  const wallpapers = wallpapersFor(theme);
  const [wallpaperId, setWallpaperId] = useState<WallpaperId>(() => readStoredId(readTheme()));
  const [themeForId, setThemeForId] = useState<Theme>(theme);
  if (themeForId !== theme) {
    setThemeForId(theme);
    setWallpaperId(readStoredId(theme));
  }
  const [customUrl, setCustomUrl] = useState<string | null>(null);
  const [hasCustom, setHasCustom] = useState(false);

  const replaceCustomUrl = useCallback((next: string | null) => {
    setCustomUrl((current) => {
      if (current) URL.revokeObjectURL(current);
      return next;
    });
  }, []);

  const refreshCustom = useCallback(async () => {
    if (typeof indexedDB === "undefined") {
      setHasCustom(false);
      replaceCustomUrl(null);
      return;
    }
    try {
      const blob = await loadCustomBlob();
      if (!blob) {
        setHasCustom(false);
        replaceCustomUrl(null);
        return;
      }
      setHasCustom(true);
      replaceCustomUrl(URL.createObjectURL(blob));
    } catch {
      setHasCustom(false);
      replaceCustomUrl(null);
    }
  }, [replaceCustomUrl]);

  useEffect(() => {
    // IndexedDB is an external store; hydrate once after mount.
    // eslint-disable-next-line react-hooks/set-state-in-effect -- load custom wallpaper blob
    void refreshCustom();

    function onChange() {
      setWallpaperId(readStoredId(readTheme()));
      void refreshCustom();
    }

    window.addEventListener(CUSTOM_EVENT, onChange);
    window.addEventListener("storage", onChange);
    return () => {
      window.removeEventListener(CUSTOM_EVENT, onChange);
      window.removeEventListener("storage", onChange);
    };
  }, [refreshCustom]);

  useEffect(() => {
    return () => {
      if (customUrl) URL.revokeObjectURL(customUrl);
    };
  }, [customUrl]);

  const setWallpaper = useCallback(
    (id: WallpaperId) => {
      if (id === "custom" && !hasCustom && !customUrl) return;
      setWallpaperId(id);
      writeStoredId(id, theme);
    },
    [customUrl, hasCustom, theme],
  );

  const setCustomWallpaper = useCallback(
    async (file: File) => {
      if (!file.type.startsWith("image/")) {
        throw new Error("Choose an image file.");
      }
      if (file.size > MAX_CUSTOM_BYTES) {
        throw new Error("Image must be 8 MB or smaller.");
      }
      await saveCustomBlob(file);
      const nextUrl = URL.createObjectURL(file);
      setHasCustom(true);
      replaceCustomUrl(nextUrl);
      setWallpaperId("custom");
      writeStoredId("custom", readTheme());
    },
    [replaceCustomUrl],
  );

  const clearCustomWallpaper = useCallback(async () => {
    await deleteCustomBlob();
    setHasCustom(false);
    replaceCustomUrl(null);
    const themeNow = readTheme();
    if (readStoredId(themeNow) === "custom") {
      const fallback = defaultWallpaperId(themeNow);
      setWallpaperId(fallback);
      writeStoredId(fallback, themeNow);
    }
  }, [replaceCustomUrl]);

  const wallpaperSrc =
    wallpaperId === "custom" && customUrl ? customUrl : builtInSrc(wallpaperId, theme);

  const value = useMemo(
    () => ({
      wallpaperId,
      wallpaperSrc,
      wallpapers,
      customSrc: customUrl,
      hasCustom,
      setWallpaper,
      setCustomWallpaper,
      clearCustomWallpaper,
    }),
    [
      clearCustomWallpaper,
      customUrl,
      hasCustom,
      setCustomWallpaper,
      setWallpaper,
      wallpaperId,
      wallpaperSrc,
      wallpapers,
    ],
  );

  return <WallpaperContext.Provider value={value}>{children}</WallpaperContext.Provider>;
}

export function useWallpaper() {
  const context = useContext(WallpaperContext);
  if (!context) {
    throw new Error("useWallpaper must be used within WallpaperProvider");
  }
  return context;
}

export function WallpaperBackdrop({
  className = "pointer-events-none absolute inset-0 bg-cover bg-center",
  opacity,
}: {
  className?: string;
  opacity?: number;
}) {
  const { wallpaperSrc } = useWallpaper();
  return (
    <div
      aria-hidden
      className={className}
      style={{
        backgroundImage: `url("${wallpaperSrc}")`,
        ...(opacity === undefined ? {} : { opacity }),
      }}
    />
  );
}
