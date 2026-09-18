// One-off snapshot builder for the offline demo.
// Downloads decisions published 2024-03-01..2024-03-05 from rozhodnuti.justice.cz
// and writes data/snapshot/decisions.jsonl.gz (one Decision JSON per line).
// Source: Ministerstvo spravedlnosti CR, rozhodnuti.justice.cz, CC BY 4.0.
// Usage: node data/scripts/build-snapshot.mjs [limit]
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const BASE = 'https://rozhodnuti.justice.cz';
const UA = 'caselaw-ai-demo (hackathon)';
const LIMIT = Number(process.argv[2] ?? 300);
const DAYS = ['2024-03-01', '2024-03-02', '2024-03-03', '2024-03-04', '2024-03-05'];
const MAX_JUSTIFICATION = 20000;
const here = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.resolve(here, '..', 'snapshot');

let lastCall = 0;
async function getJson(url) {
  const wait = 334 - (Date.now() - lastCall);           // ~3 req/s, be polite
  if (wait > 0) await new Promise(r => setTimeout(r, wait));
  lastCall = Date.now();
  const res = await fetch(url, { headers: { 'User-Agent': UA, Accept: 'application/json' } });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.json();
}

const LEX = { PREDPIS_ZAKON: 'z. č.', PREDPIS_NARIZENI_VLADY: 'nař. vl. č.', PREDPIS_VYHLASKA: 'vyhl. č.' };

function caseNumberOf(cn) {
  if (!cn) return null;
  const base = `${cn.senate ?? ''} ${cn.registry ?? ''} ${cn.index ?? ''}/${cn.year ?? ''}`.trim();
  return cn.pageNumber ? `${base}-${cn.pageNumber}` : base;
}

function toDecision(uuid, doc, meta) {
  const m = doc.metadata ?? {};
  const provisions = meta?.zminenaUstanoveni?.length
    ? meta.zminenaUstanoveni
    : (m.regulations ?? []).map(r => `§ ${r.paragraphNumber ?? ''} ${LEX[r.lexType] ?? 'č.'} ${r.lexNumber}/${r.lexYear} Sb.`);
  let justification = doc.justificationText ?? '';
  if (justification.length > MAX_JUSTIFICATION) justification = justification.slice(0, MAX_JUSTIFICATION);
  return {
    id: uuid,
    ecli: m.ecli ?? meta?.ecli ?? null,
    caseNumber: meta?.jednaciCislo ?? caseNumberOf(m.caseNumber) ?? uuid,
    court: meta?.soud ?? m.courtCode ?? 'neznámý soud',
    courtCode: m.courtCode ?? null,
    decidedOn: m.decisionAt ?? meta?.datumVydani ?? null,
    publishedOn: m.publishedAt ?? meta?.datumZverejneni ?? null,
    subject: m.caseSubject ?? meta?.predmetRizeni ?? null,
    keywords: meta?.klicovaSlova ?? m.flags ?? [],
    provisions,
    resultTypes: m.caseResultType ?? [],
    verdictText: doc.verdictText ?? null,
    justificationText: justification,
    sourceUrl: `${BASE}/api/finaldoc/${uuid}`,
  };
}

const items = [];
outer: for (const day of DAYS) {
  const [y, mo, d] = day.split('-').map(Number);
  for (let page = 0; ; page++) {
    const body = await getJson(`${BASE}/api/opendata/${y}/${mo}/${d}?page=${page}`);
    const list = Array.isArray(body) ? body : (body.items ?? []);
    for (const it of list) {
      items.push(it);
      if (items.length >= LIMIT) break outer;
    }
    const totalPages = Array.isArray(body) ? page + 1 : (body.totalPages ?? page + 1);
    if (page + 1 >= totalPages || list.length === 0) break;
  }
}
console.error(`listed ${items.length} items`);

const decisions = [];
const resultTypes = new Map(), lexTypes = new Set(), courts = new Map();
for (const [i, meta] of items.entries()) {
  const uuid = String(meta.odkaz).split('/').pop();
  try {
    const doc = await getJson(`${BASE}/api/finaldoc/${uuid}`);
    const dec = toDecision(uuid, doc, meta);
    decisions.push(dec);
    for (const rt of dec.resultTypes) resultTypes.set(rt, (resultTypes.get(rt) ?? 0) + 1);
    for (const r of doc.metadata?.regulations ?? []) lexTypes.add(r.lexType);
    courts.set(dec.court, (courts.get(dec.court) ?? 0) + 1);
  } catch (e) {
    console.error(`skip ${uuid}: ${e.message}`);
  }
  if ((i + 1) % 25 === 0) console.error(`fetched ${i + 1}/${items.length}`);
}

fs.mkdirSync(OUT_DIR, { recursive: true });
const jsonl = decisions.map(d => JSON.stringify(d)).join('\n') + '\n';
fs.writeFileSync(path.join(OUT_DIR, 'decisions.jsonl.gz'), zlib.gzipSync(Buffer.from(jsonl, 'utf8'), { level: 9 }));
fs.writeFileSync(path.join(OUT_DIR, 'stats.json'), JSON.stringify({
  count: decisions.length,
  resultTypes: Object.fromEntries([...resultTypes].sort((a, b) => b[1] - a[1])),
  lexTypes: [...lexTypes],
  courts: Object.fromEntries([...courts].sort((a, b) => b[1] - a[1])),
  keywords: Object.fromEntries([...decisions.flatMap(d => d.keywords).reduce((m, k) => m.set(k, (m.get(k) ?? 0) + 1), new Map())].sort((a, b) => b[1] - a[1])),
}, null, 2));
console.error(`wrote ${decisions.length} decisions, gz ${(fs.statSync(path.join(OUT_DIR, 'decisions.jsonl.gz')).size / 1048576).toFixed(2)} MB`);
