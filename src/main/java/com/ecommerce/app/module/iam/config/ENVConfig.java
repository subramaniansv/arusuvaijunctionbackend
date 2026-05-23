package com.ecommerce.app.module.iam.config;
import io.github.cdimascio.dotenv.Dotenv;

public class ENVConfig {
    /*
     * `.env` is optional. In production the values come from real
     * environment variables (set by setenv.sh / the container runtime).
     * `ignoreIfMalformed` + `ignoreIfMissing` keep the class from blowing
     * up if the local .env contains placeholder lines like
     *   export FOO=... BAR=...
     * which dotenv-java would otherwise reject.
     */
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .ignoreIfMalformed()
            .load();

    /**
     * Look up a config value. Order:
     *   1. process environment   (real production config - always wins)
     *   2. .env file              (developer convenience for local runs)
     * Returns null when neither has the key.
     *
     * Precedence is system-env-first on purpose: it stops a stray .env
     * that sneaks into the runtime (Docker layer, working-dir surprise,
     * etc.) from silently overriding values you set in the hosting
     * dashboard (Render, Fly, etc.).
     */
    public static String get(String key){
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            v = dotenv.get(key);
        }
        return (v == null || v.isBlank()) ? null : v;
    }
}
