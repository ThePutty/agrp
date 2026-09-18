# Snapshot rozhodnutí (offline demo)

Zdroj: Ministerstvo spravedlnosti ČR, rozhodnuti.justice.cz, CC BY 4.0

## Obsah

| Položka | Hodnota |
|---|---|
| Soubor | `decisions.jsonl.gz` (gzip JSONL, jedno rozhodnutí = jeden řádek) |
| Počet rozhodnutí | 300 |
| Datum zveřejnění | 2024-03-01 až 2024-03-04 (2024-03-01: 260, 2024-03-02: 5, 2024-03-03: 1, 2024-03-04: 34) |
| Datum vydání | 2008-06-17 až 2024-02-26 |
| Velikost | 0,80 MB komprimovaně |
| Soudy | 63 okresních, obvodních a městských soudů |
| `justificationText` | zkráceno na max. 20 000 znaků |

`stats.json` obsahuje rozpad podle soudů, klíčových slov a hodnot `caseResultType`.

## Formát řádku

Jeden serializovaný záznam `cz.demo.caselaw.domain.Decision`:

```json
{"id":"…uuid…","ecli":"ECLI:CZ:OSKT:2023:7.C.129.2023.6","caseNumber":"7 C 129/2023-44",
 "court":"Okresní soud v Klatovech","courtCode":"OSKT","decidedOn":"2023-09-07","publishedOn":"2024-03-01",
 "subject":"…","keywords":["…"],"provisions":["§ 2991 z. č. 89/2012 Sb."],"resultTypes":["VYHOVENI"],
 "verdictText":"…","justificationText":"…","sourceUrl":"https://rozhodnuti.justice.cz/api/finaldoc/…"}
```

Čte ho `cz.demo.caselaw.justice.SnapshotDecisionSource` (aktivní při `app.ingest.source=snapshot`,
cesta v `app.ingest.snapshot-path`).

## Jak snapshot znovu vytvořit

```bash
node data/scripts/build-snapshot.mjs 300
```

Skript volá `GET /api/opendata/{yyyy}/{M}/{d}?page=N` a `GET /api/finaldoc/{uuid}`,
drží limit ~3 požadavky/s a hlásí se hlavičkou `User-Agent: caselaw-ai-demo (hackathon)`.
