package com.ecommerce.app.module.iam.services;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.ecommerce.app.module.iam.models.EmailVerificationToken;
import com.ecommerce.app.module.iam.models.User;
import com.ecommerce.app.module.iam.repository.EmailVerificationRepository;
import com.ecommerce.app.module.iam.repository.UserRepository;
import com.ecommerce.app.module.mail.MailService;
import com.ecommerce.app.module.mail.MailTemplates;

/**
 * Issues email-verification tokens and verifies them when the user clicks the
 * link in the email. Raw tokens never persist in the database — only their
 * SHA-256 hashes — so a DB leak can't reveal active verification links.
 */
public class EmailVerificationService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);

    /** Link is valid for 24 hours from issue. */
    private static final long TOKEN_TTL_HOURS = 24;
    private static final SecureRandom RNG = new SecureRandom();

    private final EmailVerificationRepository repo = new EmailVerificationRepository();
    private final UserRepository users = new UserRepository();

    /**
     * Generate a fresh token for {@code user} (invalidating any older ones)
     * and dispatch the verification email. Fire-and-forget — failures are
     * logged inside {@link MailService}.
     *
     * @return true when a token was successfully persisted and queued for send.
     */
    public boolean send(User user) {
        if (user == null || user.getId() == null) return false;
        if (user.getEmail() == null || user.getEmail().isBlank()) return false;
        if (repo.isUserVerified(user.getId())) {
            LOG.debug("skip verification send: user {} already verified", user.getId());
            return false;
        }

        // Older live tokens get marked used so each request supersedes the previous.
        repo.invalidateOutstanding(user.getId());

        String raw = generateRawToken();
        EmailVerificationToken t = new EmailVerificationToken();
        t.setUserId(user.getId());
        t.setTokenHash(EmailVerificationRepository.hash(raw));
        t.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));
        EmailVerificationToken saved = repo.insert(t);
        if (saved == null) {
            LOG.warn("email verification token insert failed for user {}", user.getId());
            return false;
        }

        String url = buildVerifyUrl(raw);
        MailService.get().send(
                user.getEmail(),
                "Verify your Arusuvai email",
                MailTemplates.emailVerification(user.getFirstName(), url));
        return true;
    }

    /**
     * Verify a token from the URL. Atomic: only the first request consumes
     * the token. Returns the outcome enum so callers can render a nice page.
     */
    public Result verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Result.INVALID;
        String hash = EmailVerificationRepository.hash(rawToken.trim());
        EmailVerificationToken t = repo.findByHash(hash);
        if (t == null) return Result.INVALID;
        if (t.getUsedAt() != null) return Result.ALREADY_USED;
        if (EmailVerificationRepository.isExpired(t.getExpiresAt())) return Result.EXPIRED;

        // Consume the token first; if another request raced us, that one wins.
        if (!repo.markUsed(t.getTokenId())) return Result.ALREADY_USED;
        repo.markUserVerified(t.getUserId());
        return Result.VERIFIED;
    }

    /** Re-send a verification email for a user identified by id (used by /resend endpoint). */
    public boolean resendForUser(UUID userId) {
        if (userId == null) return false;
        User u = users.getUser(userId);
        if (u == null || u.getId() == null) return false;
        return send(u);
    }

    private static String generateRawToken() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String buildVerifyUrl(String rawToken) {
        String base = ENVConfig.get("APP_BASE_URL");
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(
                "APP_BASE_URL is not set - cannot build email verification link. "
                + "Set it in the hosting environment (e.g. Render dashboard).");
        }
        // strip trailing slash
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/api/email-verify?token=" + rawToken;
    }

    public enum Result { VERIFIED, ALREADY_USED, EXPIRED, INVALID }
}
