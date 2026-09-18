import { Panel, StatusLine, ms } from "./ui";
import type { JobStatus, Step } from "@/lib/types";

const JOB_LABEL: Record<JobStatus, string> = {
  RUNNING: "PROBÍHÁ",
  COMPLETED: "HOTOVO",
  FAILED: "SELHALO",
};

export default function ProgressSteps({
  steps,
  status,
  no = "02",
}: {
  steps: Step[];
  status: JobStatus;
  no?: string;
}) {
  const done = steps.filter((s) => s.status === "DONE" || s.status === "SKIPPED").length;
  const total = steps.length || 1;

  return (
    <Panel
      no={no}
      title="Průběh"
      help="Průběh zpracování dotazu. Kroky běží uvnitř Temporal workflow; každý uzel AI grafu hlásí stav zpět (signál), proto se řádky mění živě. Celý běh trvá obvykle 1-3 minuty, nejdéle trvají volání jazykového modelu."
      flush
      right={
        <>
          <span className={status === "FAILED" ? "red" : undefined}>
            {JOB_LABEL[status]}
            {status === "RUNNING" && <span className="cursor"> ▌</span>}
          </span>
          <span>
            {done}/{total}
          </span>
        </>
      }
    >
      <div className="grid-rules cols-6">
        {steps.map((s, i) => (
          <div key={s.name} className="cell">
            <p className="label">
              STEP {String(i + 1).padStart(2, "0")}
            </p>
            <p style={{ textTransform: "uppercase", fontSize: 11, letterSpacing: "0.04em" }}>
              {s.name}
            </p>
            <p style={{ marginTop: 6 }}>
              <StatusLine status={s.status} suffix={ms(s.durationMs)} />
            </p>
            {s.attempts != null && s.attempts > 1 && (
              <p className="label">{s.attempts}. pokus</p>
            )}
            {s.detail && (
              <p className="label" title={s.detail} style={{ textTransform: "none", letterSpacing: 0 }}>
                {s.detail}
              </p>
            )}
          </div>
        ))}
        {steps.length === 0 && <div className="cell muted">Zatím žádné kroky.</div>}
      </div>
    </Panel>
  );
}
