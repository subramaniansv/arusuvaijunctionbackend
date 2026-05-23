package com.ecommerce.app.module.mail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * Mail sender with two transports:
 *
 * <ol>
 *   <li><b>ZeptoMail HTTP API</b> (preferred on Render and any host that
 *       blocks outbound SMTP). Enabled when {@code ZEPTOMAIL_TOKEN} is set.</li>
 *   <li><b>SMTP</b> fallback - used when ZeptoMail isn't configured.
 *       Useful for local dev against Gmail / Zoho SMTP.</li>
 * </ol>
 *
 * <p>Env vars (read via {@link ENVConfig}):
 * <pre>
 *   # --- HTTP transport (ZeptoMail) ---
 *   ZEPTOMAIL_TOKEN     send-mail token ("Send Mail Token" from ZeptoMail UI)
 *   ZEPTOMAIL_URL       optional, default https://api.zeptomail.in/v1.1/email
 *   SMTP_FROM           "Arusuvai &lt;noreply@arusuvaijunction.com&gt;" (re-used for HTTP From)
 *
 *   # --- SMTP transport (fallback) ---
 *   SMTP_HOST           smtp.zoho.in
 *   SMTP_PORT           587
 *   SMTP_USERNAME       no-reply@arusuvai.com
 *   SMTP_PASSWORD       &lt;app password&gt;
 *   SMTP_STARTTLS       true   (default)
 *   SMTP_SSL            false  (default - set true for port 465 SMTPS)
 *
 *   MAIL_ENABLED        true   (default - set false to disable entirely)
 * </pre>
 *
 * <p>If neither transport is configured the service no-ops with a single
 * warning at startup so dev environments don't crash.
 */
public final class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);
    private static final String DEFAULT_ZEPTO_URL = "https://api.zeptomail.in/v1.1/email";

    private static final MailService INSTANCE = new MailService();
    public static MailService get() { return INSTANCE; }

    private enum Transport0 { HTTP_ZEPTOMAIL, SMTP, DISABLED }

    private final Transport0 transport;
    private final String fromAddress;
    private final ExecutorService executor;

    // HTTP transport state
    private final HttpClient http;
    private final String zeptoUrl;
    private final String zeptoAuthHeader;

    // SMTP transport state
    private final Session session;

    private MailService() {
        boolean mailEnabled = !"false".equalsIgnoreCase(env("MAIL_ENABLED", "true"));
        if (!mailEnabled) {
            this.transport = Transport0.DISABLED;
            this.fromAddress = null;
            this.executor = null;
            this.http = null; this.zeptoUrl = null; this.zeptoAuthHeader = null;
            this.session = null;
            LOG.warn("MailService disabled: MAIL_ENABLED=false");
            return;
        }

        String zeptoToken = env("ZEPTOMAIL_TOKEN", null);
        String configuredFrom = env("SMTP_FROM", null);

        // --- Path 1: ZeptoMail HTTP API ---
        if (!isBlank(zeptoToken)) {
            if (isBlank(configuredFrom)) {
                LOG.warn("MailService disabled: SMTP_FROM is required when ZEPTOMAIL_TOKEN is set");
                this.transport = Transport0.DISABLED;
                this.fromAddress = null; this.executor = null;
                this.http = null; this.zeptoUrl = null; this.zeptoAuthHeader = null;
                this.session = null;
                return;
            }
            this.transport = Transport0.HTTP_ZEPTOMAIL;
            this.fromAddress = configuredFrom;
            this.zeptoUrl = env("ZEPTOMAIL_URL", DEFAULT_ZEPTO_URL);
            // ZeptoMail wants the literal prefix "Zoho-enczapikey " before the token.
            String token = zeptoToken.trim();
            if (!token.toLowerCase().startsWith("zoho-enczapikey")) {
                token = "Zoho-enczapikey " + token;
            }
            this.zeptoAuthHeader = token;
            this.http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            this.session = null;
            this.executor = Executors.newFixedThreadPool(2, new MailThreadFactory());
            LOG.info("MailService enabled via ZeptoMail HTTP API (url={}, from={})", this.zeptoUrl, this.fromAddress);
            return;
        }

        // --- Path 2: SMTP fallback ---
        String host = env("SMTP_HOST", null);
        String user = env("SMTP_USERNAME", null);
        String pass = env("SMTP_PASSWORD", null);
        if (isBlank(host) || isBlank(user) || isBlank(pass)) {
            this.transport = Transport0.DISABLED;
            this.fromAddress = null; this.executor = null;
            this.http = null; this.zeptoUrl = null; this.zeptoAuthHeader = null;
            this.session = null;
            LOG.warn("MailService disabled: set ZEPTOMAIL_TOKEN (preferred) or SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD");
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

        this.transport = Transport0.SMTP;
        this.fromAddress = isBlank(configuredFrom) ? user : configuredFrom;
        this.http = null; this.zeptoUrl = null; this.zeptoAuthHeader = null;
        this.executor = Executors.newFixedThreadPool(2, new MailThreadFactory());
        LOG.info("MailService enabled via SMTP (host={}, port={}, from={})", host, port, this.fromAddress);
    }

    /** Returns true when a mail transport is configured. */
    public boolean isEnabled() { return transport != Transport0.DISABLED; }

    /**
     * Fire-and-forget. Sends the message on a background thread and
     * returns immediately. Errors are logged but never thrown to the
     * caller.
     */
    public void send(MailMessage message) {
        if (message == null) return;
        if (transport == Transport0.DISABLED) {
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
     * Synchronously send a message. Throws on any failure so the
     * caller (e.g. the admin /api/mail endpoint) can surface the error
     * in its HTTP response.
     */
    public void sendNow(MailMessage message) throws Exception {
        if (message == null) throw new IllegalArgumentException("message is required");
        if (isBlank(message.getTo())) throw new IllegalArgumentException("recipient is required");
        if (isBlank(message.getSubject())) throw new IllegalArgumentException("subject is required");
        if (isBlank(message.getBody())) throw new IllegalArgumentException("body is required");
        if (transport == Transport0.DISABLED) {
            throw new IllegalStateException("mail service is not configured (set ZEPTOMAIL_TOKEN or SMTP_*)");
        }
        if (transport == Transport0.HTTP_ZEPTOMAIL) {
            sendViaZeptoMail(message);
        } else {
            sendViaSmtp(message);
        }
    }

    // ----------------------------------------------------------------
    // HTTP (ZeptoMail) transport
    // ----------------------------------------------------------------
    private void sendViaZeptoMail(MailMessage message) throws Exception {
        InternetAddress from = parseAddress(fromAddress);
        InternetAddress[] tos = InternetAddress.parse(message.getTo(), false);
        if (tos.length == 0) throw new IllegalStateException("no recipients");

        StringBuilder toJson = new StringBuilder("[");
        for (int i = 0; i < tos.length; i++) {
            if (i > 0) toJson.append(',');
            toJson.append("{\"email_address\":{\"address\":\"").append(jsonEscape(tos[i].getAddress())).append("\"");
            if (tos[i].getPersonal() != null) {
                toJson.append(",\"name\":\"").append(jsonEscape(tos[i].getPersonal())).append("\"");
            }
            toJson.append("}}");
        }
        toJson.append(']');

        String bodyField = message.isHtml() ? "htmlbody" : "textbody";
        StringBuilder json = new StringBuilder(512);
        json.append('{')
            .append("\"from\":{\"address\":\"").append(jsonEscape(from.getAddress())).append("\"");
        if (from.getPersonal() != null) {
            json.append(",\"name\":\"").append(jsonEscape(from.getPersonal())).append("\"");
        }
        json.append("},")
            .append("\"to\":").append(toJson).append(',')
            .append("\"subject\":\"").append(jsonEscape(message.getSubject())).append("\",")
            .append('"').append(bodyField).append("\":\"").append(jsonEscape(message.getBody())).append("\"")
            .append('}');

        HttpRequest req = HttpRequest.newBuilder(URI.create(zeptoUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", zeptoAuthHeader)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = res.statusCode();
        if (code >= 200 && code < 300) {
            LOG.info("mail sent via ZeptoMail: to={} subject={} status={}",
                    message.getTo(), message.getSubject(), code);
            return;
        }
        throw new IllegalStateException("ZeptoMail HTTP " + code + ": " + truncate(res.body(), 500));
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b");  break;
                case '\f': out.append("\\f");  break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ----------------------------------------------------------------
    // SMTP transport (local dev fallback)
    // ----------------------------------------------------------------
    private void sendViaSmtp(MailMessage message) throws Exception {
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
        LOG.info("mail sent via SMTP: to={} subject={}", message.getTo(), message.getSubject());
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
