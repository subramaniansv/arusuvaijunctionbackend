package com.ecommerce.app.module.mail;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import com.ecommerce.app.module.order.Order;
import com.ecommerce.app.module.order.OrderItem;

/**
 * Pre-baked HTML templates for the transactional emails we send.
 *
 * Each helper returns a complete &lt;!doctype html&gt; document with
 * inline styles - email clients strip &lt;style&gt; blocks aggressively
 * so inline is the only safe option.
 */
public final class MailTemplates {

    private MailTemplates() { }

    private static final String BRAND_GREEN = "#0f5d3a";
    private static final String BRAND_BG    = "#fafaf7";
    private static final String BRAND_TEXT  = "#1a1a1a";
    private static final String BRAND_MUTED = "#6b7280";

    /* ------------------ Welcome on registration ------------------ */
    public static String welcome(String firstName) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:22px;color:" + BRAND_TEXT + ";'>Welcome to Arusuvai, " + name + "!</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "We're thrilled to have you with us. Arusuvai brings traditional South-Indian sweets, "
                + "savouries and homestyle treats right to your doorstep."
                + "</p>"
                + "<p style='margin:0 0 20px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Head over to our store and start exploring."
                + "</p>"
                + cta("Browse products", "https://arusuvai.local/products");
        return wrap("Welcome to Arusuvai", body);
    }

    /* ------------------ Login alert ------------------ */
    public static String loginAlert(String firstName, String ip, String userAgent) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>New sign-in to your Arusuvai account</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Hi " + name + ", we noticed a new sign-in just now."
                + "</p>"
                + "<table style='border-collapse:collapse;margin:0 0 16px;'>"
                + row("IP address", ip == null ? "unknown" : ip)
                + row("Device",     userAgent == null ? "unknown" : userAgent)
                + "</table>"
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "If this wasn't you, please reset your password immediately."
                + "</p>";
        return wrap("New sign-in to your account", body);
    }

    /* ------------------ Email verification ------------------ */
    public static String emailVerification(String firstName, String verifyUrl) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>Verify your email</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Hi " + name + ", please confirm your email address to finish setting up your Arusuvai account."
                + "</p>"
                + cta("Verify email", verifyUrl)
                + "<p style='margin:16px 0 0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "If the button doesn't work, paste this link into your browser:<br>"
                + "<span style='word-break:break-all;'>" + escape(verifyUrl) + "</span>"
                + "</p>";
        return wrap("Verify your Arusuvai email", body);
    }

    /* ------------------ Password reset ------------------ */
    public static String passwordReset(String firstName, String resetUrl) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>Reset your password</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Hi " + name + ", click the button below to set a new password. The link is valid for 30 minutes."
                + "</p>"
                + cta("Reset password", resetUrl)
                + "<p style='margin:16px 0 0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "Didn't ask for this? You can safely ignore this email."
                + "</p>";
        return wrap("Reset your Arusuvai password", body);
    }

    /* ------------------ Order placed ------------------ */
    public static String orderPlaced(String firstName, Order order) {
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);

        StringBuilder rows = new StringBuilder();
        List<OrderItem> items = order.getOrderItems();
        if (items != null) {
            for (OrderItem oi : items) {
                String label = oi.getVariantLabel() != null && !oi.getVariantLabel().isBlank()
                        ? " <span style='color:" + BRAND_MUTED + ";'>(" + escape(oi.getVariantLabel()) + ")</span>"
                        : "";
                String productName = oi.getProductName() != null
                        ? escape(oi.getProductName())
                        : "Item";
                double lineTotal = oi.getPrice() * oi.getQuantity();
                rows.append("<tr>")
                    .append("<td style='padding:8px 0;border-bottom:1px solid #eceae3;'>")
                    .append(productName).append(label)
                    .append("</td>")
                    .append("<td style='padding:8px 0;border-bottom:1px solid #eceae3;text-align:center;color:")
                    .append(BRAND_MUTED).append(";'>").append(oi.getQuantity()).append("</td>")
                    .append("<td style='padding:8px 0;border-bottom:1px solid #eceae3;text-align:right;'>")
                    .append(money.format(lineTotal)).append("</td>")
                    .append("</tr>");
            }
        }

        String orderRef = order.getOrderId() != null
                ? order.getOrderId().toString().substring(0, 8).toUpperCase()
                : "-";

        String body = ""
                + "<h1 style='margin:0 0 8px;font-size:22px;color:" + BRAND_TEXT + ";'>Thanks for your order, " + name + "!</h1>"
                + "<p style='margin:0 0 20px;line-height:1.6;color:" + BRAND_MUTED + ";'>"
                + "Order reference <strong style='color:" + BRAND_TEXT + ";'>#" + orderRef + "</strong>"
                + "</p>"
                + "<table style='width:100%;border-collapse:collapse;font-size:14px;color:" + BRAND_TEXT + ";'>"
                + "<thead><tr>"
                + "<th style='text-align:left;padding:0 0 8px;border-bottom:2px solid " + BRAND_GREEN + ";'>Item</th>"
                + "<th style='text-align:center;padding:0 0 8px;border-bottom:2px solid " + BRAND_GREEN + ";'>Qty</th>"
                + "<th style='text-align:right;padding:0 0 8px;border-bottom:2px solid " + BRAND_GREEN + ";'>Price</th>"
                + "</tr></thead><tbody>"
                + rows
                + "<tr><td colspan='2' style='padding:16px 0 4px;text-align:right;font-weight:600;'>Total</td>"
                + "<td style='padding:16px 0 4px;text-align:right;font-weight:600;color:" + BRAND_GREEN + ";'>"
                + money.format(order.getTotalAmount()) + "</td></tr>"
                + "</tbody></table>"
                + "<div style='margin-top:24px;padding:16px;background:" + BRAND_BG + ";border-radius:8px;font-size:13px;color:"
                + BRAND_TEXT + ";'>"
                + "<div style='font-weight:600;margin-bottom:4px;'>Shipping to</div>"
                + "<div style='color:" + BRAND_MUTED + ";white-space:pre-line;'>" + escape(nullSafe(order.getShippingAddress())) + "</div>"
                + "<div style='color:" + BRAND_MUTED + ";margin-top:4px;'>Phone: " + escape(nullSafe(order.getPhone())) + "</div>"
                + "</div>"
                + "<p style='margin:24px 0 0;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "We'll send you another email once your order ships."
                + "</p>";
        return wrap("Order #" + orderRef + " confirmed", body);
    }

    /* ------------------ Order status update ------------------ */
    public static String orderStatusUpdate(String firstName, Order order, String newStatus) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String orderRef = order != null && order.getOrderId() != null
                ? order.getOrderId().toString().substring(0, 8).toUpperCase()
                : "-";
        String statusKey = newStatus == null ? "" : newStatus.toUpperCase();
        String headline;
        String detail;
        switch (statusKey) {
            case "CONFIRMED":
                headline = "Your order is confirmed";
                detail   = "We've confirmed your order and started getting it ready.";
                break;
            case "SHIPPED":
                headline = "Your order is on its way";
                detail   = "Your order has shipped. You'll receive it shortly.";
                break;
            case "DELIVERED":
                headline = "Your order has been delivered";
                detail   = "We hope you enjoy your Arusuvai treats. Thank you for ordering with us!";
                break;
            case "CANCELLED":
                headline = "Your order has been cancelled";
                detail   = "If this wasn't expected, please reply to this email and we'll take a look.";
                break;
            default:
                headline = "Order update";
                detail   = "Your order status is now " + escape(statusKey) + ".";
        }
        String body = ""
                + "<h1 style='margin:0 0 12px;font-size:22px;color:" + BRAND_TEXT + ";'>" + headline + "</h1>"
                + "<p style='margin:0 0 8px;line-height:1.6;color:" + BRAND_TEXT + ";'>Hi " + name + ",</p>"
                + "<p style='margin:0 0 16px;line-height:1.6;color:" + BRAND_TEXT + ";'>" + detail + "</p>"
                + "<p style='margin:0 0 16px;line-height:1.6;color:" + BRAND_MUTED + ";'>"
                + "Order reference <strong style='color:" + BRAND_TEXT + ";'>#" + orderRef + "</strong>"
                + " &middot; Status <strong style='color:" + BRAND_GREEN + ";'>" + escape(statusKey) + "</strong>"
                + "</p>";
        return wrap("Order #" + orderRef + " - " + headline, body);
    }

    /* ------------------ Password changed ------------------ */
    public static String passwordChanged(String firstName) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>Your password was changed</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "Hi " + name + ", this is a confirmation that the password for your Arusuvai account was just changed."
                + "</p>"
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "If you didn't do this, please contact our support team right away."
                + "</p>";
        return wrap("Your Arusuvai password was changed", body);
    }

    /* ------------------ Account status change ------------------ */
    public static String accountStatusChanged(String firstName, String status) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String s = status == null ? "" : status.toUpperCase();
        String detail;
        switch (s) {
            case "ACTIVE":     detail = "Your Arusuvai account has been activated. You can sign in and place orders as usual."; break;
            case "SUSPENDED":  detail = "Your Arusuvai account has been suspended. Please contact our support team for more information."; break;
            case "INACTIVE":   detail = "Your Arusuvai account has been marked inactive."; break;
            default:           detail = "Your Arusuvai account status is now " + escape(s) + ".";
        }
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>Account status updated</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>Hi " + name + ",</p>"
                + "<p style='margin:0 0 16px;line-height:1.6;color:" + BRAND_TEXT + ";'>" + detail + "</p>"
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "Status: <strong style='color:" + BRAND_TEXT + ";'>" + escape(s) + "</strong>"
                + "</p>";
        return wrap("Arusuvai account status updated", body);
    }

    /* ------------------ Contact form acknowledgement ------------------ */
    public static String contactAck(String firstName, String subjectLine, String message) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : escape(firstName);
        String subj = (subjectLine == null || subjectLine.isBlank()) ? "your message" : escape(subjectLine);
        String msg  = (message == null || message.isBlank()) ? "" : escape(message);
        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:22px;color:" + BRAND_TEXT + ";'>Thanks for reaching out, " + name + "!</h1>"
                + "<p style='margin:0 0 12px;line-height:1.6;color:" + BRAND_TEXT + ";'>"
                + "We've received your message and our team will get in touch with you shortly."
                + "</p>"
                + "<p style='margin:0 0 16px;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "Subject: <strong style='color:" + BRAND_TEXT + ";'>" + subj + "</strong>"
                + "</p>"
                + (msg.isEmpty() ? "" :
                    "<div style='margin:0 0 20px;padding:16px;background:" + BRAND_BG + ";border-left:3px solid "
                    + BRAND_GREEN + ";border-radius:4px;font-size:14px;color:" + BRAND_TEXT
                    + ";white-space:pre-line;line-height:1.6;'>" + msg + "</div>")
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "If you need to add anything, just reply to this email and we'll see it."
                + "</p>";
        return wrap("We received your message", body);
    }

    /* ------------------ Contact form internal notification ------------------ */
    public static String contactNotify(String name, String fromEmail, String phone,
                                       String subjectLine, String message) {
        String safeName    = escape(name == null ? "(unknown)" : name);
        String safeEmail   = escape(fromEmail == null ? "(unknown)" : fromEmail);
        String safePhone   = (phone == null || phone.isBlank()) ? "-" : escape(phone);
        String safeSubject = (subjectLine == null || subjectLine.isBlank()) ? "(no subject)" : escape(subjectLine);
        String safeMessage = escape(message == null ? "" : message);

        String body = ""
                + "<h1 style='margin:0 0 16px;font-size:20px;color:" + BRAND_TEXT + ";'>New contact-form submission</h1>"
                + "<table style='border-collapse:collapse;margin:0 0 16px;'>"
                + row("From",    safeName)
                + row("Email",   safeEmail)
                + row("Phone",   safePhone)
                + row("Subject", safeSubject)
                + "</table>"
                + "<div style='margin:0 0 12px;padding:16px;background:" + BRAND_BG + ";border-left:3px solid "
                + BRAND_GREEN + ";border-radius:4px;font-size:14px;color:" + BRAND_TEXT
                + ";white-space:pre-line;line-height:1.6;'>" + safeMessage + "</div>"
                + "<p style='margin:0;line-height:1.6;color:" + BRAND_MUTED + ";font-size:13px;'>"
                + "Reply directly to <a href='mailto:" + safeEmail + "' style='color:" + BRAND_GREEN
                + ";'>" + safeEmail + "</a> to respond to the customer."
                + "</p>";
        return wrap("New contact-form submission", body);
    }

    /* ------------------ Generic admin-composed mail ------------------ */
    public static String custom(String content) {
        // The admin/custom endpoint passes user-supplied content as-is so it
        // can contain HTML. We only wrap it in the brand chrome.
        return wrap("Arusuvai", content);
    }

    /* ------------------ shared chrome ------------------ */

    private static String wrap(String previewTitle, String contentHtml) {
        return "<!doctype html><html><head>"
                + "<meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + escape(previewTitle) + "</title>"
                + "</head>"
                + "<body style='margin:0;padding:0;background:" + BRAND_BG + ";font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:"
                + BRAND_TEXT + ";'>"
                + "<table role='presentation' width='100%' style='background:" + BRAND_BG + ";padding:32px 12px;'><tr><td align='center'>"
                + "<table role='presentation' width='560' style='max-width:560px;width:100%;background:#fff;border:1px solid #eceae3;border-radius:12px;overflow:hidden;'>"
                + "<tr><td style='padding:20px 28px;background:" + BRAND_GREEN + ";color:#fff;font-size:18px;font-weight:600;letter-spacing:.01em;'>"
                + "Arusuvai"
                + "</td></tr>"
                + "<tr><td style='padding:28px;'>"
                + contentHtml
                + "</td></tr>"
                + "<tr><td style='padding:16px 28px;background:" + BRAND_BG + ";color:" + BRAND_MUTED + ";font-size:12px;text-align:center;border-top:1px solid #eceae3;'>"
                + "&copy; Arusuvai &middot; Authentic South-Indian flavours"
                + "</td></tr>"
                + "</table>"
                + "</td></tr></table>"
                + "</body></html>";
    }

    private static String cta(String label, String url) {
        return "<a href='" + escape(url) + "' "
                + "style='display:inline-block;padding:12px 22px;background:" + BRAND_GREEN + ";color:#fff;"
                + "text-decoration:none;border-radius:999px;font-weight:600;font-size:14px;'>"
                + escape(label)
                + "</a>";
    }

    private static String row(String key, String value) {
        return "<tr>"
                + "<td style='padding:4px 16px 4px 0;color:" + BRAND_MUTED + ";font-size:13px;'>" + escape(key) + "</td>"
                + "<td style='padding:4px 0;font-size:13px;color:" + BRAND_TEXT + ";'>" + escape(value) + "</td>"
                + "</tr>";
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    /** Minimal HTML escape - prevents email injection of stray tags / entities. */
    static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
