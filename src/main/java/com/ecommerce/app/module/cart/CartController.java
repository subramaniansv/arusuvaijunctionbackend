package com.ecommerce.app.module.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.module.cart.CartConverterUtil.CartItemRequest;
import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

@WebServlet("/api/cart")
public class CartController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(CartController.class);


    private final CartService service = new CartService();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }
            Cart cart = service.getCart(user.getUserId());
            SendResponseUtil.sendResponse(new ApiResponse(true, "cart fetched", cart, 200), response);
        } catch (Exception e) {
            LOG.error("exception at cart controller doGet  ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not fetch cart", null, 500), response);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }
            CartItemRequest body = CartConverterUtil.requestToItem(request);
            if (body == null || body.getProductId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "productId is required", null, 400), response);
                return;
            }
            int qty = body.getQuantity() == 0 ? 1 : body.getQuantity();
            Cart cart = service.addItem(user.getUserId(), body.getProductId(), body.getVariantId(), qty);
            SendResponseUtil.sendResponse(new ApiResponse(true, "item added to cart", cart, 200), response);
        } catch (RuntimeException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at cart controller doPost  ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not add item to cart", null, 500), response);
        }
    }

    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }
            CartItemRequest body = CartConverterUtil.requestToItem(request);
            if (body == null || body.getProductId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "productId is required", null, 400), response);
                return;
            }
            Cart cart = service.updateItem(user.getUserId(), body.getProductId(), body.getVariantId(), body.getQuantity());
            SendResponseUtil.sendResponse(new ApiResponse(true, "cart updated", cart, 200), response);
        } catch (RuntimeException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at cart controller doPut  ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not update cart", null, 500), response);
        }
    }

    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }
            String productIdParam = request.getParameter("productId");
            if (productIdParam == null || productIdParam.isEmpty()) {
                Cart cart = service.clearCart(user.getUserId());
                SendResponseUtil.sendResponse(new ApiResponse(true, "cart cleared", cart, 200), response);
                return;
            }
            UUID productId;
            try {
                productId = UUID.fromString(productIdParam);
            } catch (IllegalArgumentException ex) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "invalid productId", null, 400), response);
                return;
            }
            // variantId is optional - when absent we remove the product line
            // that has no variant attached.
            UUID variantId = null;
            String variantIdParam = request.getParameter("variantId");
            if (variantIdParam != null && !variantIdParam.isEmpty()) {
                try {
                    variantId = UUID.fromString(variantIdParam);
                } catch (IllegalArgumentException ex) {
                    SendResponseUtil.sendResponse(new ApiResponse(false, "invalid variantId", null, 400), response);
                    return;
                }
            }
            Cart cart = service.removeItem(user.getUserId(), productId, variantId);
            SendResponseUtil.sendResponse(new ApiResponse(true, "item removed from cart", cart, 200), response);
        } catch (RuntimeException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at cart controller doDelete  ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not modify cart", null, 500), response);
        }
    }
}
