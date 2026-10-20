import { baseName, joinPath, parentPath, type FileEntry, type FileList } from "@/src/lib/api/files";

const HOME = "/home/deploy";

const trees = new Map<string, Map<string, FileEntry[]>>();

/** In-memory folders for preview servers, so Files can be tried without an SSH session. */
export function listPreviewFiles(serverId: string, path: string): FileList {
  const dir = resolvePath(path);
  const entries = tree(serverId).get(dir);
  if (!entries) throw new Error("no such file or directory");
  return { path: dir, entries: entries.map((entry) => ({ ...entry })) };
}

export function createPreviewFile(serverId: string, path: string) {
  insert(tree(serverId), clean(path), "file", 0, false);
}

export function createPreviewDirectory(serverId: string, path: string) {
  insert(tree(serverId), clean(path), "dir", 0, false);
}

export function renamePreviewFile(serverId: string, from: string, to: string) {
  place(tree(serverId), tree(serverId), clean(from), clean(to), false);
}

export function deletePreviewFile(serverId: string, path: string) {
  remove(tree(serverId), clean(path));
}

export function copyPreviewFile(serverId: string, from: string, to: string, overwrite = false) {
  place(tree(serverId), tree(serverId), clean(from), clean(to), overwrite);
}

export function movePreviewFile(serverId: string, from: string, to: string, overwrite = false) {
  const store = tree(serverId);
  place(store, store, clean(from), clean(to), overwrite);
  remove(store, clean(from));
}

export function uploadPreviewFile(serverId: string, directory: string, name: string, size: number) {
  insert(tree(serverId), joinPath(resolvePath(directory), name), "file", size, true);
}

export function transferPreviewFile(
  fromId: string,
  toId: string,
  from: string,
  toDir: string,
  overwrite = false,
  removeSource = false,
  toName = "",
) {
  const source = clean(from);
  const name = toName || baseName(source);
  const destDir = toDir ? resolvePath(toDir) : HOME;
  place(tree(fromId), tree(toId), source, joinPath(destDir, name), overwrite);
  if (removeSource) remove(tree(fromId), source);
}

function tree(serverId: string) {
  let store = trees.get(serverId);
  if (!store) {
    store = seed(serverId);
    trees.set(serverId, store);
  }
  return store;
}

function seed(serverId: string) {
  const store = new Map<string, FileEntry[]>();
  const label = serverId.replace(/^sample_/, "");
  const dirs = ["/", "/home", HOME, "/tmp", "/etc", "/var", "/var/log"];
  for (const dir of dirs) store.set(dir, []);
  link(store, "/", "home", "/home", "dir");
  link(store, "/", "tmp", "/tmp", "dir");
  link(store, "/", "etc", "/etc", "dir");
  link(store, "/", "var", "/var", "dir");
  link(store, "/home", "deploy", HOME, "dir");
  link(store, HOME, `${label}.txt`, `${HOME}/${label}.txt`, "file", 128);
  link(store, "/etc", "hostname", "/etc/hostname", "file", 24);
  link(store, "/var", "log", "/var/log", "dir");
  link(store, "/var/log", "syslog", "/var/log/syslog", "file", 4096);
  link(store, "/tmp", "scratch.txt", "/tmp/scratch.txt", "file", 16);
  return store;
}

function link(
  store: Map<string, FileEntry[]>,
  parent: string,
  name: string,
  path: string,
  type: "file" | "dir",
  size = 0,
) {
  store.get(parent)?.push(entry(name, path, type, size));
  if (type === "dir" && !store.has(path)) store.set(path, []);
}

function entry(name: string, path: string, type: "file" | "dir", size: number): FileEntry {
  return {
    name,
    path,
    type,
    size,
    mode: type === "dir" ? "drwxr-xr-x" : "-rw-r--r--",
    modified: "2026-10-15T09:00:00.000Z",
  };
}

function resolvePath(path: string) {
  if (!path || path === "~" || path === "~/") return HOME;
  if (path.startsWith("~/")) return joinPath(HOME, path.slice(2));
  return clean(path);
}

function clean(path: string) {
  const parts = path.split("/").filter((part) => part && part !== ".");
  const out: string[] = [];
  for (const part of parts) {
    if (part === "..") out.pop();
    else out.push(part);
  }
  return `/${out.join("/")}`;
}

function find(store: Map<string, FileEntry[]>, path: string) {
  if (path === "/") return entry("/", "/", "dir", 0);
  return store.get(parentPath(path))?.find((item) => item.path === path);
}

function insert(
  store: Map<string, FileEntry[]>,
  path: string,
  type: "file" | "dir",
  size: number,
  overwrite: boolean,
) {
  if (path === "/") throw new Error("invalid path");
  const parent = parentPath(path);
  const siblings = store.get(parent);
  if (!siblings) throw new Error("no such file or directory");
  const name = baseName(path);
  const existing = siblings.find((item) => item.name === name);
  if (existing) {
    if (!overwrite) throw new Error("file exists");
    remove(store, existing.path);
  }
  siblings.push(entry(name, path, type, size));
  if (type === "dir") store.set(path, store.get(path) ?? []);
}

function place(
  fromStore: Map<string, FileEntry[]>,
  toStore: Map<string, FileEntry[]>,
  from: string,
  to: string,
  overwrite: boolean,
) {
  if (from === "/" || to === "/" || from === to || to.startsWith(`${from}/`)) {
    throw new Error("invalid path");
  }
  const source = find(fromStore, from);
  if (!source) throw new Error("no such file or directory");
  const parent = parentPath(to);
  if (!toStore.get(parent)) throw new Error("no such file or directory");
  const children = source.type === "dir" ? [...(fromStore.get(from) ?? [])] : [];
  insert(toStore, to, source.type, source.size, overwrite);
  for (const child of children) {
    place(fromStore, toStore, child.path, joinPath(to, child.name), overwrite);
  }
}

function remove(store: Map<string, FileEntry[]>, path: string) {
  if (path === "/") throw new Error("invalid path");
  const siblings = store.get(parentPath(path));
  const index = siblings?.findIndex((item) => item.path === path) ?? -1;
  if (!siblings || index < 0) throw new Error("no such file or directory");
  const [removed] = siblings.splice(index, 1);
  if (removed.type === "dir") {
    for (const key of [...store.keys()]) {
      if (key === path || key.startsWith(`${path}/`)) store.delete(key);
    }
  }
}
