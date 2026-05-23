package com.ecommerce.app.module.mail;

/**
 * Minimal value object describing a single outbound email.
 *
 * The mail module is intentionally simple: one recipient, one subject,
 * one body. The body may be plain text or HTML - see {@link #isHtml()}.
 */
public class MailMessage {

    private String to;
    private String subject;
    private String body;
    private boolean html = true;

    public MailMessage() { }

    public MailMessage(String to, String subject, String body) {
        this.to = to;
        this.subject = subject;
        this.body = body;
    }

    public MailMessage(String to, String subject, String body, boolean html) {
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.html = html;
    }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public boolean isHtml() { return html; }
    public void setHtml(boolean html) { this.html = html; }
}
