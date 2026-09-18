"use client";

import { Panel } from "./ui";
import type { Answer, Citation, Side } from "@/lib/types";

const SIDE_LABEL: Record<Side, string> = {
  FOR: "PRO KLIENTA",
  AGAINST: "PROTISTRANA",
  BALANCED: "NEUTRÁLNÍ",
};

export default function CitationList({
  answer,
  onSelectCitation,
  no = "09",
}: {
  answer: Answer;
  onSelectCitation: (c: Citation) => void;
  no?: string;
}) {
  const total = answer.verifiedCount + answer.unverifiedCount;
  const allOk = answer.unverifiedCount === 0;

  return (
    <Panel
      no={no}
      title="Ověření citací"
      subtitle="Každá citace je doslovně porovnána s textem rozhodnutí v databázi"
      help="Proč se ověřuje? Jazykové modely si umí vymyslet spisovou značku i citát (reálné případy u NSS a ÚS). Jak? Kód zkontroluje, že citované rozhodnutí je mezi 8 nalezenými a že úryvek v jeho textu doslova existuje (po sjednocení diakritiky a mezer). Co znamená červená? Citaci se nepodařilo ověřit, AI dostala jednu šanci na opravu; berte ji jako nedůvěryhodnou."
      flush
      right={
        <span className={allOk ? undefined : "red"}>
          {answer.verifiedCount} Z {total} OVĚŘENO
        </span>
      }
    >
      <div className="tbl-wrap">
        <table className="tbl">
          <thead>
            <tr>
              <th className="r">#</th>
              <th>Sp. zn.</th>
              <th>Strana</th>
              <th>Úryvek</th>
              <th>Ověření</th>
            </tr>
          </thead>
          <tbody>
            {answer.citations.map((c) => (
              <tr
                key={c.index}
                onClick={() => onSelectCitation(c)}
                style={{ cursor: "pointer" }}
              >
                <td className="r">{c.index}</td>
                <td className="nowrap">
                  <u>{c.caseNumber}</u>
                </td>
                <td className="nowrap label">{SIDE_LABEL[c.side]}</td>
                <td className="prose" style={{ minWidth: 320 }}>
                  „{c.quote}“
                </td>
                <td className={`nowrap state${c.verified ? "" : " state-failed"}`}>
                  {c.verified ? "■ OVĚŘENO" : `✕ NEOVĚŘENO${c.reason ? ` - ${c.reason}` : ""}`}
                </td>
              </tr>
            ))}
            {answer.citations.length === 0 && (
              <tr className="norow">
                <td colSpan={5} className="muted">
                  Model neuvedl žádné citace.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}
