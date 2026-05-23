package com.ecommerce.app.module.mail;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Admin-only endpoint for sending an ad-hoc email to any recipient.
 *
 *   POST /api/mail
 *   Authorization: Bearer &lt;admin token&gt;
 *   {
 *     "to":      "customer@example.com",
 *     "subject": "Re: your enquiry",
 *     "content": "&lt;p&gt;Hi there...&lt;/p&gt;",
 *     "html":    true        // optional, defaults to true
 *   }
 *
 * Returns 200 on success, 400 on validation failure, 502 on SMTP failure.
 *
 * <p>This is a synchronous send (uses {@link MailService#sendNow}) so
 * the admin gets immediate feedback about whether SMTP accepted the
 * message. Transactional emails fired from other modules (register,
 * checkout, ...) go through the fire-and-forget {@link MailService#send}.
 */
@WebServlet("/api/mail")
@RequiresRole("Admin")
public class MailController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(MailController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JsonNode body;
        try {
            body = MAPPER.readTree(request.getReader());
        } catch (Exception e) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "invalid JSON body", null, 400), response);
            return;
        }
        if (body == null || body.isMissingNode() || body.isNull()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "request body is required", null, 400), response);
            return;
        }

        String to      = text(body, "to");
        String subject = text(body, "subject");
        String content = text(body, "content");
        if (content == null) content = text(body, "body");
        boolean html   = !body.has("html") || body.path("html").asBoolean(true);

        if (isBlank(to) || isBlank(subject) || isBlank(content)) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "to, subject and content are required", null, 400), response);
            return;
        }

        // Wrap the admin's payload in our brand chrome unless they pass
        // raw=true (escape hatch for fully-custom HTML).
        boolean raw = body.path("raw").asBoolean(false);
        String finalBody = (raw || !html) ? content : MailTemplates.custom(content);

        try {
            MailService.get().sendNow(new MailMessage(to, subject, finalBody, html));
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "mail sent", null, 200), response);
        } catch (IllegalStateException e) {
            // Service disabled.
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, e.getMessage(), null, 503), response);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.warn("admin mail send failed: {}", e.getMessage());
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "mail delivery failed: " + e.getMessage(), null, 502), response);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
