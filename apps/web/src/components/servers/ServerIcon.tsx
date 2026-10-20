"use client";

import { useEffect, useState } from "react";
import { getServerIconBlob } from "@/src/lib/api/server";
import { serverIcon } from "@/src/lib/server-icons";

const blobCache = new Map<string, string>();

/** Preset or uploaded image for one server. Status is never drawn into the icon. */
export function ServerIcon({
  iconId,
  customIcon = false,
  serverId,
  updatedAt,
  preview,
  size,
}: {
  iconId?: string;
  customIcon?: boolean;
  serverId?: string;
  updatedAt?: string;
  /** Local data URL while choosing an image. Empty string hides a saved custom image. */
  preview?: string | null;
  size: number;
}) {
  const preset = serverIcon(iconId);
  const Preset = preset.Icon;
  const cacheKey = serverId ? `${serverId}:${updatedAt || ""}` : "";
  const showSaved = customIcon && preview == null && Boolean(serverId);
  const showPreview = Boolean(preview);
  const cached = showSaved && cacheKey ? blobCache.get(cacheKey) : undefined;
  const [loaded, setLoaded] = useState<{ key: string; src: string } | null>(null);
  const [failedKey, setFailedKey] = useState("");

  useEffect(() => {
    if (!showSaved || !serverId || !cacheKey || blobCache.has(cacheKey)) return;
    let cancelled = false;
    void getServerIconBlob(serverId)
      .then((blob) => {
        const url = URL.createObjectURL(blob);
        blobCache.set(cacheKey, url);
        if (!cancelled) setLoaded({ key: cacheKey, src: url });
      })
      .catch(() => {
        if (!cancelled) setFailedKey(cacheKey);
      });
    return () => {
      cancelled = true;
    };
  }, [showSaved, serverId, cacheKey]);

  const failed = failedKey === cacheKey;
  const savedSrc = cached || (loaded?.key === cacheKey ? loaded.src : "");
  const src = showPreview ? preview || "" : showSaved && !failed ? savedSrc : "";
  if (src) {
    return (
      // User-chosen image. next/image does not accept blob or data URLs here.
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={src}
        width={size}
        height={size}
        alt=""
        aria-hidden
        draggable={false}
        onError={() => setFailedKey(cacheKey)}
        className="shrink-0 rounded-[22%] bg-black/5 object-contain dark:bg-white/8"
        style={{ width: size, height: size }}
      />
    );
  }

  return (
    <Preset
      size={size}
      className={size >= 22 ? "shrink-0 drop-shadow-[0_1px_1px_rgba(0,0,0,0.16)]" : "shrink-0"}
    />
  );
}
