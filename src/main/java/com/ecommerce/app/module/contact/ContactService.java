package com.ecommerce.app.module.contact;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.ecommerce.app.module.mail.MailService;
import com.ecommerce.app.module.mail.MailTemplates;

public class ContactService {
    private static final Logger LOG = LoggerFactory.getLogger(ContactService.class);
    private static final Pattern EMAIL_RX =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final ContactRepository repo = new ContactRepository();

    public ContactMessage submit(String name, String email, String phone,
                                 String subject, String message, UUID userId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (email == null || email.isBlank() || !EMAIL_RX.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("a valid email is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (name.length() > 120) throw new IllegalArgumentException("name too long");
        if (message.length() > 5000) throw new IllegalArgumentException("message too long");

        ContactMessage m = new ContactMessage();
        m.setName(name.trim());
        m.setEmail(email.trim().toLowerCase());
        m.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        m.setSubject(subject == null || subject.isBlank() ? null : subject.trim());
        m.setMessage(message.trim());
        m.setUserId(userId);
        m.setStatus("NEW");
        ContactMessage saved = repo.insert(m);

        // Fire-and-forget acknowledgement to the submitter. Failures are
        // logged inside MailService and never surface to the caller.
        if (saved != null) {
            try {
                String firstName = m.getName().split("\\s+")[0];
                String html = MailTemplates.contactAck(firstName, m.getSubject(), m.getMessage());
                MailService.get().send(m.getEmail(),
                        "We received your message - Arusuvai",
                        html);
            } catch (Exception e) {
                LOG.warn("contact ack mail dispatch failed: {}", e.getMessage());
            }

            // Internal notification to support inbox. Scope: contact form only.
            try {
                String supportTo = ENVConfig.get("CONTACT_NOTIFY_TO");
                if (supportTo == null || supportTo.isBlank()) {
                    supportTo = "support@arusuvaijunction.com";
                }
                String notifySubject = "[Contact] "
                        + (m.getSubject() == null || m.getSubject().isBlank()
                                ? "New message from " + m.getName()
                                : m.getSubject() + " - " + m.getName());
                String notifyHtml = MailTemplates.contactNotify(
                        m.getName(), m.getEmail(), m.getPhone(),
                        m.getSubject(), m.getMessage());
                MailService.get().send(supportTo, notifySubject, notifyHtml);
            } catch (Exception e) {
                LOG.warn("contact support notify dispatch failed: {}", e.getMessage());
            }
        }
        return saved;
    }

    public Map<String, Object> list(int limit, int offset, String status) {
        if (limit <= 0 || limit > 100) limit = 20;
        if (offset < 0) offset = 0;
        List<ContactMessage> rows = repo.findAll(limit, offset, status);
        int total = repo.countAll(status);
        Map<String, Object> out = new HashMap<>();
        out.put("messages", rows);
        out.put("total", total);
        out.put("limit", limit);
        out.put("offset", offset);
        return out;
    }

    public boolean markStatus(UUID id, String status) {
        if (id == null) return false;
        String s = status == null ? "" : status.toUpperCase();
        if (!s.equals("NEW") && !s.equals("READ") && !s.equals("REPLIED") && !s.equals("ARCHIVED")) {
            throw new IllegalArgumentException("invalid status; allowed NEW, READ, REPLIED, ARCHIVED");
        }
        return repo.updateStatus(id, s);
    }

    public boolean delete(UUID id) {
        return id != null && repo.delete(id);
    }
}
