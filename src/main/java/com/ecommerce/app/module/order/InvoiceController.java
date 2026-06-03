package com.ecommerce.app.module.order;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

/**
 * Serves a downloadable, server-generated PDF invoice.
 *
 *   GET /api/order/invoice?orderID=<uuid>  ->  application/pdf (attachment)
 *
 * The endpoint sits behind the global AuthorizationFilter, so a valid
 * bearer token is required. Crucially, the order is re-fetched from the
 * database via {@link OrderService#getOrderById}, which scopes the lookup
 * to the authenticated user (admins excepted). Nothing about the invoice -
 * prices, totals, address, items - is taken from the client; the frontend
 * only supplies the order id, and a user can only ever fetch their own
 * orders.
 */
@WebServlet("/api/order/invoice")
public class InvoiceController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(InvoiceController.class);

    private final OrderService orderService = new OrderService();
    private final InvoiceService invoiceService = new InvoiceService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
            return;
        }

        String orderIdParam = request.getParameter("orderID");
        if (orderIdParam == null || orderIdParam.isBlank()) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "orderID is required", null, 400), response);
            return;
        }

        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdParam);
        } catch (IllegalArgumentException ex) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid orderID", null, 400), response);
            return;
        }

        // Ownership is enforced inside getOrderById: a regular user only ever
        // gets their own order back (null otherwise).
        Order order = orderService.getOrderById(orderId);
        if (order == null || order.getOrderId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "order not found", null, 404), response);
            return;
        }

        try {
            byte[] pdf = invoiceService.generate(order);

            String filename = "invoice-" + order.getOrderId().toString().substring(0, 8) + ".pdf";
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLength(pdf.length);
            // The PDF embeds personal order data; keep intermediaries from caching it.
            response.setHeader("Cache-Control", "no-store");

            try (OutputStream os = response.getOutputStream()) {
                os.write(pdf);
                os.flush();
            }
        } catch (Exception e) {
            LOG.error("invoice generation failed for order {}", orderId, e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not generate invoice", null, 500), response);
        }
    }
}
