package com.ecommerce.app.module.health;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tiny public liveness endpoint for uptime monitors (UptimeRobot,
 * BetterStack, Render's own healthcheck, etc).
 *
 *   GET /ping  -> 200 "pong"  (plain text, no DB call, no JSON parsing)
 *   HEAD /ping -> 200 (empty body) — UptimeRobot's default HEAD probe.
 *
 * Use this in preference to /health when all you need is "is the JVM
 * accepting traffic?" — it avoids waking the Neon serverless DB on
 * every 5-minute ping. For deeper checks (DB pool, latency) use
 * /health, /health/live or /health/ready.
 */
@WebServlet(urlPatterns = { "/ping" })
public class PingController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("text/plain; charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.getWriter().write("pong");
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse res) {
        res.setStatus(HttpServletResponse.SC_OK);
        res.setHeader("Cache-Control", "no-store");
    }
}
