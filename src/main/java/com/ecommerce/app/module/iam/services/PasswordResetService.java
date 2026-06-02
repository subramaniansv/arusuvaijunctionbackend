package com.ecommerce.app.module.iam.services;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.ecommerce.app.module.iam.models.PasswordResetToken;
import com.ecommerce.app.module.iam.models.User;
import com.ecommerce.app.module.iam.repository.PasswordResetRepository;
import com.ecommerce.app.module.iam.repository.UserRepository;
import com.ecommerce.app.module.mail.MailService;
import com.ecommerce.app.module.mail.MailTemplates;

/**
 * Forgot-password flow.
 *
 *   1. {@link #requestReset(String)} - user supplies their email; if it maps to
 *      an account we issue a single-use token and email a reset link. The raw
 *      token never persists in the DB (only its SHA-256 hash), so a DB leak
 *      can't reveal active links.
 *   2. {@link #reset(String, String)} - user clicks the link, supplies the raw
 *      token + a new password. The token is consumed atomically and the
 *      password is overwritten WITHOUT requiring the old password.
 *
 * To avoid leaking which emails are registered, {@link #requestReset(String)}
 * is intentionally silent about whether the email existed - the controller
 * always returns the same generic response.
 */
public class PasswordResetService {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

    /** Reset link is valid for 30 minutes from issue (matches the email copy). */
    private static final long TOKEN_TTL_MINUTES = 30;
    /** Minimum password length - matches the self-service password change policy. */
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final SecureRandom RNG = new SecureRandom();

    private final PasswordResetRepository repo = new PasswordResetRepository();
    private final UserRepository users = new UserRepository();

    /**
     * Issue a reset token for the account owning {@code email} and email the
     * link. No-op (returns false) when the email is blank or unknown - callers
     * MUST NOT surface the difference to the client.
     */
    public boolean requestReset(String email) {
        if (email == null || email.isBlank()) return false;
        User user = users.getUserWithPassword(email.trim());
        if (user == null || user.getId() == null) {
            LOG.debug("password reset requested for unknown email");
            return false;
        }

        // Older live tokens get superseded so only one link is ever valid.
        repo.invalidateOutstanding(user.getId());

        String raw = generateRawToken();
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(user.getId());
        t.setTokenHash(PasswordResetRepository.hash(raw));
        t.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES));
        PasswordResetToken saved = repo.insert(t);
        if (saved == null) {
            LOG.warn("password reset token insert failed for user {}", user.getId());
            return false;
        }

        String url = buildResetUrl(raw);
        MailService.get().send(
                user.getEmail(),
                "Reset your Arusuvai password",
                MailTemplates.passwordReset(user.getFirstName(), url));
        return true;
    }

    /**
     * Consume a reset token and set a new password. Atomic: only the first
     * request consumes the token. Returns the outcome enum so callers can give
     * an accurate response.
     */
    public Result reset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) return Result.INVALID;
        if (newPassword == null || newPassword.isBlank()
                || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return Result.WEAK_PASSWORD;
        }

        String hash = PasswordResetRepository.hash(rawToken.trim());
        PasswordResetToken t = repo.findByHash(hash);
        if (t == null) return Result.INVALID;
        if (t.getUsedAt() != null) return Result.ALREADY_USED;
        if (PasswordResetRepository.isExpired(t.getExpiresAt())) return Result.EXPIRED;

        // Consume the token first; if another request raced us, that one wins.
        if (!repo.markUsed(t.getTokenId())) return Result.ALREADY_USED;

        boolean ok = users.resetPassword(t.getUserId(), newPassword);
        return ok ? Result.RESET : Result.INVALID;
    }

    private static String generateRawToken() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * Build the link the user clicks. Points at the frontend reset page
     * (APP_HOME_URL), which collects the new password and POSTs it back to
     * {@code /api/password-reset/confirm} along with the token.
     */
    private static String buildResetUrl(String rawToken) {
        String base = ENVConfig.get("APP_HOME_URL");
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(
                "APP_HOME_URL is not set - cannot build password reset link. "
                + "Set it in the hosting environment (e.g. Render dashboard).");
        }
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/reset-password?token=" + rawToken;
    }

    public enum Result { RESET, ALREADY_USED, EXPIRED, INVALID, WEAK_PASSWORD }
}
