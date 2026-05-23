package com.ecommerce.app.module.admin;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.ecommerce.app.module.order.*;
import com.ecommerce.app.module.product.Product;
import com.ecommerce.app.module.product.ProductService;

import java.util.*;
@WebServlet("/api/admin")
@RequiresRole("Admin")
public class AdminController extends HttpServlet {
    OrderService orderService = new OrderService();
    ProductService productService = new ProductService();
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            String path = request.getPathInfo();
            if (path == null) {
                path = "";
            }
            int limit = 10;
            int offset = 0;
            try {
                if (request.getParameter("limit") != null) {
                    limit = Integer.parseInt(request.getParameter("limit"));
                }
                if (request.getParameter("offset") != null) {
                    offset = Integer.parseInt(request.getParameter("offset"));
                }
            } catch (NumberFormatException nfe) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "limit and offset must be integers", null, 400), response);
                return;
            }

            String type = request.getParameter("type");
            if (type == null) {
                type = path.toLowerCase();
            } else {
                type = type.toLowerCase();
            }

            if(type.contains("order"))    {
                SendResponseUtil.sendResponse(new ApiResponse(true, "get all orders of the platform", getAllOrders(limit, offset), 200), response);
            }else if(type.contains("product")){
                  SendResponseUtil.sendResponse(new ApiResponse(true, "get all products of the platform", getAllProducts(limit, offset), 200), response);
            }else {
                  SendResponseUtil.sendResponse(new ApiResponse(false, "unknown admin resource (use ?type=order or ?type=product)", null, 400), response);
            }
        }

        /**
         * Admin-only: update an order's status.
         * PUT /api/admin?orderId=<uuid>&status=<PENDING|SHIPPED|DELIVERED|CANCELLED|...>
         */
        protected void doPut(HttpServletRequest request, HttpServletResponse response) {
            String orderIdParam = request.getParameter("orderId");
            String statusParam = request.getParameter("status");
            if (orderIdParam == null || orderIdParam.isBlank()
                    || statusParam == null || statusParam.isBlank()) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "orderId and status are required", null, 400), response);
                return;
            }
            java.util.UUID orderId;
            try {
                orderId = java.util.UUID.fromString(orderIdParam);
            } catch (IllegalArgumentException ex) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "invalid orderId", null, 400), response);
                return;
            }
            OrderStatus status;
            try {
                status = OrderStatus.valueOf(statusParam.toUpperCase());
            } catch (IllegalArgumentException ex) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "invalid status; allowed: "
                                + java.util.Arrays.toString(OrderStatus.values()), null, 400),
                        response);
                return;
            }
            try {
                boolean ok = orderService.updateOrderStatus(orderId, status);
                if (!ok) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "order not found", null, 404), response);
                    return;
                }
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "order status updated", null, 200), response);
            } catch (RuntimeException re) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, re.getMessage(), null, 400), response);
            }
        }

        private List<Order> getAllOrders(int limit,int offset){
            return orderService.getAllOrders(limit, offset);
        }
        private List<Product> getAllProducts(int limit,int offset){
            return productService.getAllProducts(limit, offset);
        }
}
