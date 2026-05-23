package com.ecommerce.app.module.health;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.config.DBConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Health probes.
 *
 *   GET /health         -> overall summary (200 if all critical checks pass, 503 otherwise)
 *   GET /health/live    -> liveness  (process is up; always 200 unless the JVM is dying)
 *   GET /health/ready   -> readiness (DB reachable, pool healthy; 200/503)
 *
 * All endpoints are unauthenticated. Use these for Kubernetes, load
 * balancer, or uptime-monitor probes.
 */
@WebServlet(urlPatterns = { "/health", "/health/*" })
public class HealthController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long STARTUP_MS = System.currentTimeMillis();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String sub = req.getPathInfo();
        if (sub == null) {
            sub = "";
        }
        switch (sub) {
            case "/live":
                writeJson(res, 200, liveBody());
                return;
            case "/ready":
                Map<String, Object> ready = readyBody();
                writeJson(res, "UP".equals(ready.get("status")) ? 200 : 503, ready);
                return;
            default:
                Map<String, Object> all = summaryBody();
                writeJson(res, "UP".equals(all.get("status")) ? 200 : 503, all);
        }
    }

    // --- check bodies -------------------------------------------------------

    private Map<String, Object> liveBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("uptimeMs", System.currentTimeMillis() - STARTUP_MS);
        return body;
    }

    private Map<String, Object> readyBody() {
        Map<String, Object> db = checkDatabase();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP".equals(db.get("status")) ? "UP" : "DOWN");
        body.put("checks", Map.of("db", db));
        return body;
    }

    private Map<String, Object> summaryBody() {
        Map<String, Object> db = checkDatabase();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP".equals(db.get("status")) ? "UP" : "DOWN");
        body.put("uptimeMs", System.currentTimeMillis() - STARTUP_MS);
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("db", db);
        checks.put("jvm", jvmInfo());
        body.put("checks", checks);
        return body;
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> info = new LinkedHashMap<>();
        long start = System.nanoTime();
        try (Connection c = DBConfig.getConnection()) {
            boolean ok = c.isValid(2); // 2-second timeout
            info.put("status", ok ? "UP" : "DOWN");
        } catch (Exception e) {
            LOG.warn("health: db check failed", e);
            info.put("status", "DOWN");
            info.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        info.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
        try {
            HikariDataSource ds = (HikariDataSource) DBConfig.getDataSource();
            Map<String, Object> pool = new LinkedHashMap<>();
            pool.put("active", ds.getHikariPoolMXBean().getActiveConnections());
            pool.put("idle", ds.getHikariPoolMXBean().getIdleConnections());
            pool.put("total", ds.getHikariPoolMXBean().getTotalConnections());
            pool.put("waiting", ds.getHikariPoolMXBean().getThreadsAwaitingConnection());
            pool.put("max", ds.getMaximumPoolSize());
            info.put("pool", pool);
        } catch (Exception ignored) {
            // pool stats are best-effort; never fail the health response on this
        }
        return info;
    }

    private Map<String, Object> jvmInfo() {
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("status", "UP");
        jvm.put("processors", rt.availableProcessors());
        jvm.put("heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        jvm.put("heapMaxMb", rt.maxMemory() / (1024 * 1024));
        return jvm;
    }

    private void writeJson(HttpServletResponse res, int status, Object body) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        try (PrintWriter w = res.getWriter()) {
            w.write(MAPPER.writeValueAsString(body));
        }
    }
}
