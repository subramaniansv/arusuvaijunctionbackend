package com.ecommerce.app.module.iam.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Verifies a Google Identity Services <em>ID token</em> (the JWT the browser
 * receives from the "Sign in with Google" button) and extracts the profile.
 *
 * <p>Rather than pulling in the heavyweight {@code google-api-client} stack
 * (which also fights the shade plugin), we delegate the cryptographic check to
 * Google's hosted {@code tokeninfo} endpoint:</p>
 *
 * <pre>GET https://oauth2.googleapis.com/tokeninfo?id_token=&lt;credential&gt;</pre>
 *
 * <p>Google validates the signature, issuer and expiry server-side and returns
 * the decoded claims as JSON (HTTP 200) — or an error for anything invalid. We
 * then enforce the two checks Google can't do for us:</p>
 * <ol>
 *   <li><b>aud</b> must equal our own OAuth client id ({@code GOOGLE_CLIENT_ID})
 *       — otherwise a token minted for a <em>different</em> site would be
 *       accepted (the classic "audience confusion" attack).</li>
 *   <li><b>email_verified</b> must be {@code true} — we never auto-create an
 *       account for an unverified Google email.</li>
 * </ol>
 *
 * <p>Uses the okhttp client that is already on the classpath (MinIO depends on
 * it) and the existing Jackson mapper, so this adds zero new dependencies.</p>
 */
public final class GoogleTokenVerifier {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";
    private static final Set<String> VALID_ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");

    private GoogleTokenVerifier() { }

    /** Verified Google profile. All string fields are non-null (possibly empty). */
    public static final class GoogleProfile {
        public final String email;
        public final String firstName;
        public final String lastName;

        GoogleProfile(String email, String firstName, String lastName) {
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    /**
     * Verify a Google ID token and return the caller's profile.
     *
     * @throws RuntimeException with a user-safe message if the token is
     *         invalid, the audience doesn't match, or the email is unverified.
     */
    public static GoogleProfile verify(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new RuntimeException("missing Google credential");
        }
        String clientId = ENVConfig.get("GOOGLE_CLIENT_ID");
        if (clientId == null || clientId.isBlank()) {
            // Misconfiguration, not the user's fault — but don't leak details.
            LOG.error("GOOGLE_CLIENT_ID is not configured; cannot verify Google sign-in");
            throw new RuntimeException("Google sign-in is not configured");
        }

        JsonNode claims = fetchTokenInfo(credential);
        if (claims == null) {
            throw new RuntimeException("invalid Google credential");
        }

        // 1. Audience must be OUR client id.
        String aud = claims.path("aud").asText("");
        if (!clientId.equals(aud)) {
            LOG.warn("Google token audience mismatch: expected our client id, got '{}'", aud);
            throw new RuntimeException("invalid Google credential");
        }

        // 2. Issuer sanity check (tokeninfo already validates, belt-and-braces).
        String iss = claims.path("iss").asText("");
        if (!VALID_ISSUERS.contains(iss)) {
            LOG.warn("Google token issuer mismatch: '{}'", iss);
            throw new RuntimeException("invalid Google credential");
        }

        // 3. Email must be present and Google-verified.
        String email = claims.path("email").asText("").trim().toLowerCase();
        boolean emailVerified = "true".equalsIgnoreCase(claims.path("email_verified").asText("false"));
        if (email.isEmpty()) {
            throw new RuntimeException("Google account has no email");
        }
        if (!emailVerified) {
            throw new RuntimeException("Your Google email is not verified");
        }

        // Names: prefer the structured given/family name; fall back to the
        // display name's first word, then the email local-part, so we never
        // store an empty first name.
        String fullName = claims.path("name").asText("").trim();
        String firstName = claims.path("given_name").asText("").trim();
        String lastName = claims.path("family_name").asText("").trim();
        if (firstName.isEmpty()) {
            if (!fullName.isEmpty()) {
                firstName = fullName.split("\\s+")[0];
            } else {
                firstName = email.substring(0, email.indexOf('@'));
            }
        }

        return new GoogleProfile(email, firstName, lastName);
    }

    /** Call Google's tokeninfo endpoint; returns the claims JSON or null. */
    private static JsonNode fetchTokenInfo(String credential) {
        String url = TOKENINFO_URL + URLEncoder.encode(credential, StandardCharsets.UTF_8);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                // 4xx here means Google rejected the token (bad signature/expired).
                return null;
            }
            return MAPPER.readTree(response.body().string());
        } catch (Exception e) {
            LOG.error("failed to reach Google tokeninfo endpoint", e);
            // Network failure — surface as an invalid credential to the caller.
            return null;
        }
    }
}
