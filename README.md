# Arusuvai Junction

E-commerce platform for authentic South-Indian sweets & savouries.

- **Backend** — Java 21, embedded Tomcat 10.1 (Jakarta EE 10), HikariCP, Neon Postgres
- **Frontend** — React + Vite, axios, React Query, React Router
- **Payments** — Razorpay Standard Checkout (2-phase: order → signature verify)
- **Storage** — Cloudflare R2 for product images
- **Mail** — Zoho SMTP for email verification, order confirmations, password resets
- **Search** — Postgres FTS (GIN index) with Elasticsearch optional

## Run locally

```bash
cp .env.example .env       # fill in DB, Razorpay, SMTP, R2 creds
docker compose up --build  # frontend → :5173, backend → :8080/arusuvai
```

Without Docker:

```bash
mvn -DskipTests package
java -jar target/ecommerce.jar &
cd frontend/ecommerce && npm install && npm run dev
```

## Deploy to Render

Push to GitHub, then in Render: **New → Blueprint → pick this repo**. The
included [`render.yaml`](render.yaml) provisions both services. Fill in the
`sync: false` env vars in the dashboard.
