# Frontend - Judikatura AI · ověřené citace

Next.js 16 (App Router, TypeScript, Tailwind CSS 4). Mluví přímo se Spring GraphQL
backendem přes malý klient `src/lib/graphql.ts` (fetch POST, žádné Apollo).

## Vývoj

```bash
npm install
npm run dev              # http://localhost:3000
```

Bez běžícího backendu použijte mock režim:

```bash
NEXT_PUBLIC_MOCK=1 npm run dev          # bash
$env:NEXT_PUBLIC_MOCK="1"; npm run dev  # PowerShell
```

V mock režimu se nevolá síť; `src/lib/mockData.ts` vrací Research, který s každým
pollem postoupí o jednu fázi (6 kroků, živý graf, 3 zásahy, 4 citace z toho jedna
neověřená, statistika výsledků).

## Build

```bash
npm run build   # produkční build (output: standalone)
npm run lint
npm start
```

## Proměnné prostředí

| Proměnná                   | Výchozí                        | Význam                                        |
| -------------------------- | ------------------------------ | --------------------------------------------- |
| `NEXT_PUBLIC_GRAPHQL_URL`  | `http://localhost:8081/graphql` | endpoint Spring GraphQL                       |
| `NEXT_PUBLIC_MOCK`         | -                              | `1` = lokální demo data, backend se nevolá     |

Obě jsou `NEXT_PUBLIC_*`, tedy se zapékají při buildu (v Dockeru jde o build ARG).

## Docker

```bash
docker build -t judikatura-fe \
  --build-arg NEXT_PUBLIC_GRAPHQL_URL=http://localhost:8081/graphql .
docker run -p 3000:3000 judikatura-fe
```

## Struktura

- `src/app/page.tsx` - Hledání: formulář, průběh, živý graf, výsledky, citace, trace
- `src/app/corpus/page.tsx` - statistiky korpusu a spuštění Temporal ingestu
- `src/app/architecture/page.tsx` - statické schéma architektury
- `src/lib/types.ts` - typy zrcadlící `backend/src/main/resources/graphql/schema.graphqls`
- `src/components/LiveGraph.tsx` - barvení uzlů Mermaid grafu podle `graph.nodes[].status`
