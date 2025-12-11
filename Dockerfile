# Build stage
FROM cgr.dev/chainguard/wolfi-base AS builder

RUN apk add --no-cache nodejs-22 npm
RUN npm install -g pnpm

WORKDIR /app

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

# Production stage
FROM cgr.dev/chainguard/wolfi-base AS runner

RUN apk add --no-cache nodejs-22

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]
