package com.ecommerce.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Closes the HikariCP pool when the webapp is undeployed or Tomcat is shut down
 * so we don't leak Postgres connections across redeploys.
 */
@WebListener
public class DBLifecycleListener implements ServletContextListener {
    private static final Logger LOG = LoggerFactory.getLogger(DBLifecycleListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Force the pool to spin up at startup so the first request doesn't
        // pay the connection-acquisition cost.
        DBConfig.getDataSource();
        LOG.info("HikariCP pool initialized at context startup");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            DBConfig.shutdown();
            LOG.info("HikariCP pool shut down at context destroy");
        } catch (Exception e) {
            LOG.error("failed to shut down HikariCP pool", e);
        }
    }
}
