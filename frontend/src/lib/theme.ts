"use client";

import { useSyncExternalStore } from "react";

export type Theme = "light" | "dark";

const STORAGE_KEY = "theme";

/** Barvy pro věci, které CSS proměnné neumí obarvit (Mermaid SVG). */
export const PALETTE: Record<Theme, { paper: string; box: string; ink: string; muted: string; red: string }> = {
  light: { paper: "#f4f4f0", box: "#ffffff", ink: "#111111", muted: "#555555", red: "#d0021b" },
  dark: { paper: "#0e0e0e", box: "#161616", ink: "#ededed", muted: "#9a9a9a", red: "#ff4d5e" },
};

/** Spouští se inline v <head>, aby se téma nastavilo před prvním vykreslením. */
export const THEME_BOOT_SCRIPT = `(function(){try{var t=localStorage.getItem("${STORAGE_KEY}");if(t!=="dark"&&t!=="light"){t=window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light";}document.documentElement.dataset.theme=t;}catch(e){}})();`;

export function currentTheme(): Theme {
  if (typeof document === "undefined") return "light";
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

export function setTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // localStorage může být nedostupné (private mode) - téma platí jen pro tuto stránku
  }
}

/** Sleduje atribut data-theme na <html>, takže se přepnutí propíše do všech komponent. */
function subscribe(onChange: () => void) {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => observer.disconnect();
}

export function useTheme(): Theme {
  return useSyncExternalStore(subscribe, currentTheme, () => "light");
}
