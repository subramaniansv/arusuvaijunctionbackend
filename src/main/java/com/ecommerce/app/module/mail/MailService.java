package com.ecommerce.app.module.mail;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Tiny SMTP-based mail sender.
 *
 * Reads its configuration from environment / .env (via {@link ENVConfig}):
 * <pre>
 *   SMTP_HOST          smtp.gmail.com
 *   SMTP_PORT          587
 *   SMTP_USERNAME      no-reply@arusuvai.com
 *   SMTP_PASSWORD      &lt;app password&gt;
 *   SMTP_FROM          "Arusuvai &lt;no-reply@arusuvai.com&gt;"     (optional)
 *   SMTP_STARTTLS      true   (default)
 *   SMTP_SSL           false  (default - set true to use port 465 SMTPS)
 *   MAIL_ENABLED       true   (default - set false to disable entirely)
 * </pre>
 *
 * <p>If {@code SMTP_HOST} or credentials are missing the service logs once
 * and silently no-ops. This keeps dev environments without SMTP from
 * crashing registration / checkout.
 *
 * <p>Sending is dispatched onto a small background thread pool so HTTP
 * request handlers never block on SMTP latency. Callers that need a
 * synchronous send can use {@link #sendNow(MailMessage)}.
 */
public final class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private static final MailService INSTANCE = new MailService();
    public static MailService get() { return INSTANCE; }

    private final boolean enabled;
    private final Session session;
    private final String fromAddress;
    private final ExecutorService executor;

    private MailService() {
        boolean mailEnabled = !"false".equalsIgnoreCase(env("MAIL_ENABLED", "true"));
        String host = env("SMTP_HOST", null);
        String user = env("SMTP_USERNAME", null);
        String pass = env("SMTP_PASSWORD", null);

        if (!mailEnabled || isBlank(host) || isBlank(user) || isBlank(pass)) {
            this.enabled = false;
            this.session = null;
            this.fromAddress = null;
            this.executor = null;
            LOG.warn("MailService disabled: set SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD in .env to enable email delivery");
            return;
        }

        String port = env("SMTP_PORT", "587");
        boolean useSsl = "true".equalsIgnoreCase(env("SMTP_SSL", "false"));
        boolean useTls = !useSsl && !"false".equalsIgnoreCase(env("SMTP_STARTTLS", "true"));

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        if (useSsl) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        final String authUser = user;
        final String authPass = pass;
        this.session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(authUser, authPass);
            }
        });

        String configuredFrom = env("SMTP_FROM", null);
        this.fromAddress = isBlank(configuredFrom) ? user : configuredFrom;
        this.enabled = true;
        this.executor = Executors.newFixedThreadPool(2, new MailThreadFactory());
        LOG.info("MailService enabled (host={}, port={}, from={})", host, port, this.fromAddress);
    }

    /** Returns true when SMTP is configured and outbound mail will be attempted. */
    public boolean isEnabled() { return enabled; }

    /**
     * Fire-and-forget. Sends the message on a background thread and
     * returns immediately. Errors are logged but never thrown to the
     * caller.
     */
    public void send(MailMessage message) {
        if (message == null) return;
        if (!enabled) {
            LOG.debug("mail skipped (service disabled): to={} subject={}", message.getTo(), message.getSubject());
            return;
        }
        executor.submit(() -> {
            try {
                sendNow(message);
            } catch (Exception e) {
                LOG.warn("mail send failed: to={} subject={} : {}",
                        message.getTo(), message.getSubject(), e.getMessage());
            }
        });
    }

    /** Convenience overload. */
    public void send(String to, String subject, String htmlBody) {
        send(new MailMessage(to, subject, htmlBody));
    }

    /**
     * Synchronously send a message. Throws on any SMTP failure so the
     * caller (e.g. the admin /api/mail endpoint) can surface the error
     * in its HTTP response.
     */
    public void sendNow(MailMessage message) throws Exception {
        if (message == null) throw new IllegalArgumentException("message is required");
        if (isBlank(message.getTo())) throw new IllegalArgumentException("recipient is required");
        if (isBlank(message.getSubject())) throw new IllegalArgumentException("subject is required");
        if (isBlank(message.getBody())) throw new IllegalArgumentException("body is required");
        if (!enabled) {
            throw new IllegalStateException("mail service is not configured (set SMTP_HOST/USERNAME/PASSWORD)");
        }

        MimeMessage mime = new MimeMessage(session);
        mime.setFrom(parseAddress(fromAddress));
        mime.setRecipients(Message.RecipientType.TO, InternetAddress.parse(message.getTo(), false));
        mime.setSubject(message.getSubject(), StandardCharsets.UTF_8.name());
        if (message.isHtml()) {
            mime.setContent(message.getBody(), "text/html; charset=UTF-8");
        } else {
            mime.setText(message.getBody(), StandardCharsets.UTF_8.name());
        }
        mime.setSentDate(new java.util.Date());
        Transport.send(mime);
        LOG.info("mail sent: to={} subject={}", message.getTo(), message.getSubject());
    }

    private static InternetAddress parseAddress(String raw) throws Exception {
        InternetAddress[] parsed = InternetAddress.parse(raw, false);
        if (parsed.length == 0) {
            throw new IllegalStateException("invalid SMTP_FROM: " + raw);
        }
        return parsed[0];
    }

    private static String env(String key, String fallback) {
        String value = null;
        try {
            value = ENVConfig.get(key);
        } catch (Exception ignored) { /* .env not loaded - fall through */ }
        if (isBlank(value)) {
            value = System.getenv(key);
        }
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static final class MailThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "mail-sender-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
