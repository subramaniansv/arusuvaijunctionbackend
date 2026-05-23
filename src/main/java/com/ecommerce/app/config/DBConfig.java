package com.ecommerce.app.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DBConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DBConfig.class);

    // All connection details come from the environment (loaded from .env
    // locally via ENVConfig, or injected by the container runtime in prod).
    // Required:
    //   DB_URL        e.g. jdbc:postgresql://<host>/<db>?sslmode=require
    //   DB_USERNAME
    //   DB_PASSWORD
    // Optional:
    //   DB_POOL_MAX       (default 10)
    //   DB_POOL_MIN_IDLE  (default 2)
    private static final HikariDataSource DATA_SOURCE = buildDataSource();

    private static HikariDataSource buildDataSource() {
        String url = required("DB_URL");
        String user = required("DB_USERNAME");
        String pass = required("DB_PASSWORD");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");
        config.setPoolName("ecommerce-pool");
        config.setMaximumPoolSize(intEnv("DB_POOL_MAX", 10));
        config.setMinimumIdle(intEnv("DB_POOL_MIN_IDLE", 2));
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setLeakDetectionThreshold(30_000);
        return new HikariDataSource(config);
    }

    private static String required(String key) {
        String value = ENVConfig.get(key);
        if (value == null || value.isBlank()) {
            LOG.error("required env var {} is not set - aborting startup", key);
            throw new IllegalStateException("missing required env var: " + key);
        }
        return value;
    }

    private static int intEnv(String key, int fallback) {
        String value = ENVConfig.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warn("env var {} is not a valid integer ({}); using {}", key, value, fallback);
            return fallback;
        }
    }

    public static DataSource getDataSource() {
        return DATA_SOURCE;
    }

    public static Connection getConnection() {
        try {
            return DATA_SOURCE.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Postgres connection failed", e);
        }
    }

    public static void shutdown() {
        if (!DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }
}