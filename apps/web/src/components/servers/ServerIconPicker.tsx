"use client";

import { useRef } from "react";
import { SERVER_ICONS, serverIcon, type ServerIconId } from "@/src/lib/server-icons";

/** Compact preset grid. Selection is a check plus a highlight, not color alone. */
export function ServerIconPicker({
  value,
  onChange,
}: {
  value: string;
  onChange: (id: ServerIconId) => void;
}) {
  const selected = serverIcon(value).id;
  const buttons = useRef<Array<HTMLButtonElement | null>>([]);

  function move(from: number, delta: number) {
    const next = (from + delta + SERVER_ICONS.length) % SERVER_ICONS.length;
    const id = SERVER_ICONS[next].id;
    onChange(id);
    buttons.current[next]?.focus();
  }

  return (
    <div
      role="radiogroup"
      aria-label="Server icon"
      className="grid w-max max-w-full grid-cols-8 gap-1 overflow-x-auto"
      onKeyDown={(event) => {
        const index = SERVER_ICONS.findIndex((item) => item.id === selected);
        if (event.key === "ArrowRight" || event.key === "ArrowDown") {
          event.preventDefault();
          move(index, 1);
        } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
          event.preventDefault();
          move(index, -1);
        }
      }}
    >
      {SERVER_ICONS.map((item, index) => {
        const Icon = item.Icon;
        const checked = item.id === selected;
        return (
          <button
            key={item.id}
            ref={(node) => {
              buttons.current[index] = node;
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            aria-label={item.label}
            title={item.label}
            tabIndex={checked ? 0 : -1}
            data-checked={checked}
            onClick={() => onChange(item.id)}
            className="relative flex size-10 items-center justify-center rounded-[8px] outline-none transition focus-visible:ring-2 focus-visible:ring-[var(--finder-accent)] data-[checked=true]:bg-[color-mix(in_srgb,var(--finder-accent)_16%,transparent)] data-[checked=true]:shadow-[inset_0_0_0_1.5px_var(--finder-accent)] hover:bg-[var(--finder-nav-hover)]"
          >
            <Icon size={26} className="shrink-0 drop-shadow-[0_1px_1px_rgba(0,0,0,0.18)]" />
            {checked ? <span className="sr-only">Selected</span> : null}
          </button>
        );
      })}
    </div>
  );
}
