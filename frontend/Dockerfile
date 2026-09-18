# syntax=docker/dockerfile:1

# --- build ---------------------------------------------------------------
FROM node:24-alpine AS builder
WORKDIR /app

# NEXT_PUBLIC_* se zapéká do buildu, proto jako build ARG
ARG NEXT_PUBLIC_GRAPHQL_URL=http://localhost:8081/graphql
ARG NEXT_PUBLIC_MOCK=
ENV NEXT_PUBLIC_GRAPHQL_URL=$NEXT_PUBLIC_GRAPHQL_URL
ENV NEXT_PUBLIC_MOCK=$NEXT_PUBLIC_MOCK
ENV NEXT_TELEMETRY_DISABLED=1

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- runtime -------------------------------------------------------------
FROM node:24-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup -g 1001 -S nodejs && adduser -u 1001 -S nextjs -G nodejs

COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
