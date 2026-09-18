const ESCAPE_RE = /[.*+?^${}()|[\]\\]/g;

function normalize(term: string) {
  return term.trim().replace(/\s+/g, " ");
}

/**
 * Zvýrazní v textu zadané výrazy (citované věty, klíčová slova) inverzí
 * inkoust/papír. Delší výrazy mají přednost, aby se nepřekrývaly.
 */
export default function Highlight({
  text,
  terms,
}: {
  text: string;
  terms: string[];
}) {
  const cleaned = [...new Set(terms.map(normalize).filter((t) => t.length >= 3))].sort(
    (a, b) => b.length - a.length,
  );
  if (cleaned.length === 0) return <>{text}</>;

  const pattern = new RegExp(
    `(${cleaned.map((t) => t.replace(ESCAPE_RE, "\\$&")).join("|")})`,
    "gi",
  );
  const parts = text.split(pattern);

  return (
    <>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <mark key={i} className="hl">
            {part}
          </mark>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </>
  );
}
