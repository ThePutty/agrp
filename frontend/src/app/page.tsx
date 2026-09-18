"use client";

import { useEffect, useRef, useState } from "react";
import AboutSystem from "@/components/AboutSystem";
import AnalysisPanel from "@/components/AnalysisPanel";
import ArgumentColumns from "@/components/ArgumentColumns";
import CitationList from "@/components/CitationList";
import DecisionDrawer from "@/components/DecisionDrawer";
import HitList from "@/components/HitList";
import LiveGraph from "@/components/LiveGraph";
import OutcomeChart from "@/components/OutcomeChart";
import ProgressSteps from "@/components/ProgressSteps";
import QuestionForm from "@/components/QuestionForm";
import TechnicalTrace from "@/components/TechnicalTrace";
import VerdictCard from "@/components/VerdictCard";
import { MOCK_MODE, ask, fetchResearch } from "@/lib/graphql";
import type { Citation, Research } from "@/lib/types";

const POLL_MS = 1000;

interface DrawerState {
  decisionId: string;
  quotes: string[];
}

export default function HomePage() {
  const [research, setResearch] = useState<Research | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [drawer, setDrawer] = useState<DrawerState | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  function stopPolling() {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
  }

  /** Dotazuje se na stav analýzy každou sekundu, dokud běží. */
  async function poll(id: string) {
    try {
      const next = await fetchResearch(id);
      if (!next) throw new Error("Analýza nebyla nalezena.");
      setResearch(next);
      if (next.status === "RUNNING") {
        timer.current = setTimeout(() => void poll(id), POLL_MS);
      } else {
        setBusy(false);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  async function onSubmit(question: string) {
    stopPolling();
    setError(null);
    setResearch(null);
    setDrawer(null);
    setBusy(true);
    try {
      const started = await ask(question);
      setResearch(started);
      if (started.status === "RUNNING") {
        timer.current = setTimeout(() => void poll(started.id), POLL_MS);
      } else {
        setBusy(false);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  const answer = research?.answer ?? null;

  function quotesFor(decisionId: string) {
    return (answer?.citations ?? [])
      .filter((x) => x.decisionId === decisionId)
      .map((x) => x.quote);
  }

  function openCitation(c: Citation) {
    const quotes = quotesFor(c.decisionId);
    setDrawer({ decisionId: c.decisionId, quotes: quotes.length ? quotes : [c.quote] });
  }

  function openDecision(decisionId: string) {
    setDrawer({ decisionId, quotes: quotesFor(decisionId) });
  }

  const terms = research?.analysis
    ? [...research.analysis.legalConcepts, ...research.analysis.provisions]
    : [];

  return (
    <>
      <section className="box">
        <header className="box-head">
          <h1 className="hero-title">Judikatura AI · ověřené citace</h1>
          <div className="meta">
            <span>REV 0.1</span>
            <span>{MOCK_MODE ? "MOCK REŽIM" : "LIVE"}</span>
          </div>
        </header>
        <div className="box-body">
          <p className="prose">
            Popište případ. Najdeme relevantní rozhodnutí, sestavíme argumenty pro i
            proti a každou citaci ověříme proti databázi.
          </p>
          {MOCK_MODE && (
            <p className="label" style={{ marginTop: 6 }}>
              Mock režim - data jsou ukázková, backend se nevolá
            </p>
          )}
        </div>
      </section>

      <AboutSystem />

      <QuestionForm onSubmit={(q) => void onSubmit(q)} busy={busy} />

      {error && (
        <section className="box">
          <header className="box-head">
            <h2 className="red">Chyba</h2>
          </header>
          <div className="box-body prose red">{error}</div>
        </section>
      )}

      {research && (
        <>
          <ProgressSteps steps={research.steps} status={research.status} no="02" />

          {research.status === "FAILED" && (
            <section className="box">
              <header className="box-head">
                <h2 className="red">Analýza selhala</h2>
              </header>
              <div className="box-body prose red">
                {research.steps.find((s) => s.status === "FAILED")?.detail ??
                  "Podrobnosti najdete v technické stopě níže."}
              </div>
            </section>
          )}

          <LiveGraph graph={research.graph} />

          {research.analysis && <AnalysisPanel analysis={research.analysis} />}

          {research.hits.length > 0 && (
            <HitList hits={research.hits} terms={terms} onSelect={openDecision} />
          )}

          {research.outcome && <OutcomeChart outcome={research.outcome} />}

          {answer && (
            <>
              <ArgumentColumns answer={answer} onSelectCitation={openCitation} />
              <VerdictCard answer={answer} />
              <CitationList answer={answer} onSelectCitation={openCitation} />
            </>
          )}

          <TechnicalTrace
            trace={research.trace}
            decisionIds={research.hits.map((h) => h.decision.id)}
            defaultOpen={research.status === "FAILED"}
            no="10"
          />
        </>
      )}

      {drawer && (
        <DecisionDrawer
          key={drawer.decisionId}
          decisionId={drawer.decisionId}
          quotes={drawer.quotes}
          onClose={() => setDrawer(null)}
        />
      )}
    </>
  );
}
