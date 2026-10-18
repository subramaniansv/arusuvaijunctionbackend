package com.ecommerce.app.module.order;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared daemon executor for fire-and-forget post-checkout side effects
 * (loyalty visit recording, notifications, other hooks).
 *
 * <p>These tasks must never block the checkout HTTP response, must never
 * throw back to the request thread, and are safe to drop on shutdown
 * (daemon threads). Each submitted task is wrapped so any exception is
 * logged and swallowed.
 */
public final class OrderAsync {

    private static final Logger LOG = LoggerFactory.getLogger(OrderAsync.class);

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "order-hook");
        t.setDaemon(true);
        return t;
    });

    private OrderAsync() {}

    /**
     * Runs {@code task} on a background thread. Any exception is logged
     * (tagged with {@code label}) and never propagated.
     */
    public static void run(String label, Runnable task) {
        POOL.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOG.warn("async order hook '{}' failed (non-fatal): {}", label, e.getMessage());
            }
        });
    }
}
