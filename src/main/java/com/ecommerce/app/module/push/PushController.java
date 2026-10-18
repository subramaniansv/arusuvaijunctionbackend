package com.ecommerce.app.module.push;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Web Push subscription endpoints. The AuthorizationFilter leaves
 *   GET /api/push/vapidPublicKey
 * open (so the SPA can fetch the key before asking permission), but requires a
 * valid JWT for subscribe / unsubscribe — exactly matching the requirement
 * that only a logged-in user receives notifications.
 *
 *   GET    /api/push/vapidPublicKey   -> { publicKey }          (public)
 *   POST   /api/push/subscribe        -> store subscription     (auth)
 *          body: { endpoint, keys: { p256dh, auth } }
 *   POST   /api/push/unsubscribe      -> remove subscription    (auth)
 *          body: { endpoint }
 */
@WebServlet("/api/push")
public class PushController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(PushController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PushRepository repo = new PushRepository();
    private final PushService service = PushService.get();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String sub = request.getPathInfo(); // mounted on /api/push/* ? see Main mapping
        if (sub != null && sub.equals("/vapidPublicKey")) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "vapid public key",
                            Map.of("publicKey",
                                    service.getPublicKey() == null ? "" : service.getPublicKey()),
                            200),
                    response);
            return;
        }
        SendResponseUtil.sendResponse(new ApiResponse(false, "unknown push action", null, 404), response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "authentication required", null, 401), response);
            return;
        }
        String sub = request.getPathInfo();
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (sub != null && sub.equals("/unsubscribe")) {
                String endpoint = body != null && body.hasNonNull("endpoint")
                        ? body.get("endpoint").asText() : null;
                if (endpoint == null || endpoint.isBlank()) {
                    SendResponseUtil.sendResponse(new ApiResponse(false, "endpoint required", null, 400), response);
                    return;
                }
                repo.deleteByEndpoint(endpoint);
                SendResponseUtil.sendResponse(new ApiResponse(true, "unsubscribed", null, 200), response);
                return;
            }

            // default: subscribe
            String endpoint = body != null && body.hasNonNull("endpoint") ? body.get("endpoint").asText() : null;
            JsonNode keys = body != null ? body.get("keys") : null;
            String p256dh = keys != null && keys.hasNonNull("p256dh") ? keys.get("p256dh").asText() : null;
            String auth   = keys != null && keys.hasNonNull("auth")   ? keys.get("auth").asText()   : null;

            if (endpoint == null || p256dh == null || auth == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "endpoint, keys.p256dh and keys.auth are required", null, 400),
                        response);
                return;
            }
            String ua = request.getHeader("User-Agent");
            repo.save(user.getUserId(), endpoint, p256dh, auth, ua);
            SendResponseUtil.sendResponse(new ApiResponse(true, "subscribed", null, 200), response);

        } catch (RuntimeException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("push controller error", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not process subscription", null, 500), response);
        }
    }
}
