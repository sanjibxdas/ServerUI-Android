"use client";

import { ACCENT_PRESETS, useAppearance } from "@/src/lib/appearance";
import { useDockPrefs } from "@/src/lib/dock-prefs";

export function AppearanceSettings() {
  const { prefs, updatePrefs } = useAppearance();
  const { hideMode, setHideMode } = useDockPrefs();

  return (
    <section className="shell-appearance" aria-label="Aura appearance">
      <div className="shell-section-label">Quick settings</div>
      <div className="shell-tiles">
        <button
          type="button"
          aria-pressed={prefs.compact}
          onClick={() => updatePrefs({ compact: !prefs.compact })}
        >
          Compact
        </button>
        <button
          type="button"
          aria-pressed={prefs.reduced}
          onClick={() => updatePrefs({ reduced: !prefs.reduced })}
        >
          Less motion
        </button>
        <button
          type="button"
          aria-pressed={prefs.clock12}
          onClick={() => updatePrefs({ clock12: !prefs.clock12 })}
        >
          12h clock
        </button>
        <button
          type="button"
          aria-pressed={prefs.right}
          onClick={() => updatePrefs({ right: !prefs.right })}
        >
          Bar right
        </button>
        <button
          type="button"
          aria-pressed={hideMode === "auto"}
          onClick={() => setHideMode(hideMode === "auto" ? "always" : "auto")}
        >
          Fade app rail
        </button>
      </div>
      <div className="shell-section-label">Palette</div>
      <div className="shell-swatches">
        {Object.entries(ACCENT_PRESETS).map(([name, color]) => (
          <button
            key={name}
            type="button"
            title={name}
            aria-label={name}
            aria-pressed={prefs.accent.toLowerCase() === color}
            onClick={() => updatePrefs({ accent: color })}
          >
            <span style={{ background: color }} />
            {name}
          </button>
        ))}
      </div>
      <label>
        Accent
        <input
          type="color"
          aria-label="Accent"
          value={prefs.accent}
          onChange={(event) => updatePrefs({ accent: event.target.value })}
        />
      </label>
      {(
        [
          ["blur", "Blur", 0, 30, "px"],
          ["opacity", "Opacity", 60, 100, "%"],
          ["radius", "Corners", 6, 32, "px"],
        ] as const
      ).map(([key, label, min, max, unit]) => (
        <label key={key}>
          {label}
          <input
            type="range"
            aria-label={label}
            min={min}
            max={max}
            value={prefs[key]}
            onChange={(event) => updatePrefs({ [key]: Number(event.target.value) })}
          />
          <output>
            {prefs[key]}
            {unit}
          </output>
        </label>
      ))}
    </section>
  );
}
