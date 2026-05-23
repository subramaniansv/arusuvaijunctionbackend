package com.ecommerce.app.module.iam.config;

import java.sql.Connection;

/**
 * Thin delegate to {@link com.ecommerce.app.config.DBConfig} so both modules
 * share a single HikariCP pool instead of opening fresh JDBC connections per call.
 */
public class DBConfig {
    private DBConfig() {}

    public static Connection getConnection() {
        return com.ecommerce.app.config.DBConfig.getConnection();
    }
}