import type { ReactNode } from "react";
import type { StepStatus } from "@/lib/types";

/**
 * Datasheetový panel: 1px rámeček, hlavička s číslem sekce vlevo a metou vpravo.
 */
/**
 * Nápověda „?“ s bublinou. Vlastní CSS tooltip (ne atribut title), aby fungoval spolehlivě
 * na hover i na klávesnici (focus) a byl čitelný v obou tématech.
 */
export function Help({ tip, label = "?" }: { tip: string; label?: string }) {
  return (
    <span className="help" tabIndex={0} data-tip={tip} aria-label={tip} role="note">
      {label}
    </span>
  );
}

export function Panel({
  no,
  title,
  subtitle,
  help,
  right,
  children,
  flush = false,
  className = "",
}: {
  /** Pořadové číslo sekce, např. "01" - vykreslí se jako `01 · TITULEK`. */
  no?: string;
  title?: string;
  subtitle?: string;
  /** Vysvětlení pro začátečníka; zobrazí se jako „?“ za titulkem. */
  help?: string;
  right?: ReactNode;
  children: ReactNode;
  flush?: boolean;
  className?: string;
}) {
  return (
    <section className={`box ${className}`}>
      {(title || right) && (
        <header className="box-head">
          <h2>
            {no && <span className="muted">{no} · </span>}
            {title}
            {help && <Help tip={help} />}
          </h2>
          {right && <div className="meta">{right}</div>}
        </header>
      )}
      {subtitle && <p className="box-sub">{subtitle}</p>}
      <div className={`box-body${flush ? " flush" : ""}`}>{children}</div>
    </section>
  );
}

const STATUS_GLYPH: Record<StepStatus, string> = {
  DONE: "■",
  RUNNING: "▌",
  PENDING: "○",
  FAILED: "✕",
  SKIPPED: "-",
};

const STATUS_LABEL: Record<StepStatus, string> = {
  DONE: "DONE",
  RUNNING: "RUNNING",
  PENDING: "PENDING",
  FAILED: "FAILED",
  SKIPPED: "SKIPPED",
};

export function statusClass(status: StepStatus) {
  if (status === "FAILED") return "state state-failed";
  if (status === "PENDING" || status === "SKIPPED") return "state state-pending";
  return "state";
}

export function StatusIcon({ status }: { status: StepStatus }) {
  return (
    <span className={statusClass(status)} aria-label={STATUS_LABEL[status]}>
      <span className={status === "RUNNING" ? "cursor" : undefined}>
        {STATUS_GLYPH[status]}
      </span>
    </span>
  );
}

/** Glyf + slovní stav, např. `■ DONE 4 876 ms`. */
export function StatusLine({
  status,
  suffix,
}: {
  status: StepStatus;
  suffix?: string;
}) {
  return (
    <span className={statusClass(status)}>
      <span className={status === "RUNNING" ? "cursor" : undefined}>
        {STATUS_GLYPH[status]}
      </span>{" "}
      {STATUS_LABEL[status]}
      {suffix ? ` ${suffix}` : ""}
    </span>
  );
}

/** Čísla s českým oddělovačem tisíců (12 345). */
export function num(value: number) {
  return value.toLocaleString("cs-CZ");
}

export function ms(value: number | null | undefined) {
  if (value == null) return "-";
  if (value < 1000) return `${value} ms`;
  return `${(value / 1000).toFixed(1).replace(".", ",")} s`;
}

export function pct(share: number) {
  return `${(share * 100).toFixed(1).replace(".", ",")} %`;
}

/** Vodorovný proužek pro skóre / podíly - 1px rámeček, inkoustová výplň. */
export function Bar({
  value,
  max = 1,
}: {
  value: number | null;
  max?: number;
}) {
  const ratio = value == null || max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
  return (
    <span className="bar">
      <i style={{ width: `${ratio * 100}%` }} />
    </span>
  );
}
