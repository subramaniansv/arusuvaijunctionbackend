# ─── Stage 1: build the Vite bundle ─────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY . .
# Backend URL the bundled JS will call. Override at build time:
#   docker build --build-arg VITE_API_BASE=https://api.example.com/arusuvai .
# When unset, the bundle calls "/arusuvai" (same origin — works behind nginx).
ARG VITE_API_BASE=/arusuvai
ENV VITE_API_BASE=$VITE_API_BASE
RUN npm run build

# ─── Stage 2: nginx static serve + reverse proxy ───────────────────
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
