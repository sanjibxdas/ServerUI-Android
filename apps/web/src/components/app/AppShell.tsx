"use client";

import { Desktop } from "@/src/components/desktop/Desktop";
import { AppearanceEffects } from "@/src/lib/appearance";
import { RuntimeProvider } from "@/src/lib/runtime";
import { SessionProvider } from "@/src/lib/session";
import { ThemeProvider } from "@/src/lib/theme";
import { WallpaperProvider } from "@/src/lib/wallpaper";

export function AppShell() {
  return (
    <ThemeProvider>
      <AppearanceEffects />
      <WallpaperProvider>
        <RuntimeProvider>
          <SessionProvider>
            <AppScreens />
          </SessionProvider>
        </RuntimeProvider>
      </WallpaperProvider>
    </ThemeProvider>
  );
}

function AppScreens() {
  return (
    <div className="h-dvh w-full">
      <Desktop />
    </div>
  );
}
