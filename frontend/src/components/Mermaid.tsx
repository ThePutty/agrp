"use client";

import { useEffect, useState } from "react";
import { PALETTE, useTheme } from "@/lib/theme";

let renderSeq = 0;

/**
 * Vykreslí Mermaid diagram v monochromatickém datasheet stylu.
 * Mermaid se načítá dynamicky (jen na klientu). Pokud se diagram nepodaří
 * naparsovat, zobrazí se surový text.
 */
export default function Mermaid({
  chart,
  className = "",
}: {
  chart: string;
  className?: string;
}) {
  const [svg, setSvg] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const theme = useTheme();

  useEffect(() => {
    const c = PALETTE[theme];
    let cancelled = false;
    (async () => {
      try {
        const mermaid = (await import("mermaid")).default;
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: "loose",
          suppressErrorRendering: true,
          theme: "base",
          fontFamily:
            '"Berkeley Mono", ui-monospace, "Cascadia Mono", "SF Mono", Menlo, Consolas, monospace',
          themeVariables: {
            background: c.box,
            primaryColor: c.box,
            primaryTextColor: c.ink,
            primaryBorderColor: c.ink,
            lineColor: c.ink,
            textColor: c.ink,
            secondaryColor: c.box,
            tertiaryColor: c.box,
            secondaryBorderColor: c.ink,
            tertiaryBorderColor: c.ink,
            clusterBkg: c.box,
            clusterBorder: c.ink,
            edgeLabelBackground: c.box,
            titleColor: c.ink,
            nodeBorder: c.ink,
            mainBkg: c.box,
            fontFamily:
              '"Berkeley Mono", ui-monospace, "Cascadia Mono", "SF Mono", Menlo, Consolas, monospace',
            fontSize: "12px",
          },
          flowchart: { curve: "linear", padding: 10, useMaxWidth: true },
        });
        const id = `mmd-${++renderSeq}`;
        const out = await mermaid.render(id, chart);
        if (cancelled) return;
        setSvg(out.svg);
        setError(null);
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : String(e));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [chart, theme]);

  if (error && !svg) {
    return (
      <div className={className}>
        <p className="label red" style={{ marginBottom: 6 }}>
          Diagram se nepodařilo vykreslit ({error}) - zdrojový text:
        </p>
        <pre className="pre">{chart}</pre>
      </div>
    );
  }

  return (
    <div
      className={`mermaid-host ${className}`}
      // svg pochází z Mermaidu vykresleného v prohlížeči
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
