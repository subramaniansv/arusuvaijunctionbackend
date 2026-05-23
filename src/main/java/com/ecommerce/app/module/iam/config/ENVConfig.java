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
     *   1. .env file (when present)
     *   2. process environment
     * Returns null when neither has the key.
     */
    public static String get(String key){
        String v = dotenv.get(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key);
        }
        return v;
    }
}
