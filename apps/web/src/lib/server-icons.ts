import type { ComponentType } from "react";
import {
  AiGpuArt,
  BackupArt,
  CloudArt,
  ContainersArt,
  CustomArt,
  DatabaseArt,
  DesktopArt,
  DevelopmentArt,
  HomelabArt,
  NetworkArt,
  ProductionArt,
  SecurityArt,
  ServerArt,
  StagingArt,
  StorageArt,
  WebArt,
  type ServerArtProps,
} from "@/src/components/servers/server-icon-art";

type ServerArtIcon = ComponentType<ServerArtProps>;

/** Stable ids shared with apps/server/internal/servers/icons.go. */
export const DEFAULT_SERVER_ICON = "server";

export const SERVER_ICONS = [
  { id: "server", label: "Server", keywords: ["general", "machine"], Icon: ServerArt },
  { id: "cloud", label: "Cloud", keywords: ["vps", "hosted"], Icon: CloudArt },
  { id: "homelab", label: "Homelab", keywords: ["home", "house"], Icon: HomelabArt },
  { id: "production", label: "Production", keywords: ["live", "prod"], Icon: ProductionArt },
  { id: "development", label: "Development", keywords: ["dev", "code"], Icon: DevelopmentArt },
  { id: "staging", label: "Staging", keywords: ["test", "qa"], Icon: StagingArt },
  { id: "ai-gpu", label: "AI / GPU", keywords: ["gpu", "ml", "compute"], Icon: AiGpuArt },
  { id: "database", label: "Database", keywords: ["sql", "db"], Icon: DatabaseArt },
  { id: "storage", label: "Storage", keywords: ["nas", "disk"], Icon: StorageArt },
  { id: "network", label: "Network", keywords: ["router", "switch"], Icon: NetworkArt },
  { id: "web", label: "Web Server", keywords: ["http", "site"], Icon: WebArt },
  { id: "containers", label: "Containers", keywords: ["docker", "pod"], Icon: ContainersArt },
  { id: "security", label: "Security", keywords: ["firewall", "shield"], Icon: SecurityArt },
  { id: "backup", label: "Backup", keywords: ["archive"], Icon: BackupArt },
  { id: "desktop", label: "Desktop", keywords: ["pc", "workstation"], Icon: DesktopArt },
  { id: "custom", label: "Custom", keywords: ["other"], Icon: CustomArt },
] as const satisfies readonly {
  id: string;
  label: string;
  keywords: string[];
  Icon: ServerArtIcon;
}[];

export type ServerIconId = (typeof SERVER_ICONS)[number]["id"];

export function isServerIconId(id: string): id is ServerIconId {
  return SERVER_ICONS.some((item) => item.id === id);
}

export function serverIcon(id: string | undefined) {
  return SERVER_ICONS.find((item) => item.id === id) ?? SERVER_ICONS[0];
}
