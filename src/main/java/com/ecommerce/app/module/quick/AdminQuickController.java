package com.ecommerce.app.module.quick;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

/**
 * Admin-only management of quick-delivery stores, allowed pincodes,
 * dynamic radius, and per-store inventory.
 *
 * POST /api/admin/quick?action=createStore
 *   { name, contactName, contactPhone, address, lat, lng, pincode, serviceRadiusKm }
 *
 * POST /api/admin/quick?action=setRadius   { storeId, serviceRadiusKm }
 * POST /api/admin/quick?action=setActive   { storeId, active }
 * POST /api/admin/quick?action=addPincode  { storeId, pincode }
 * POST /api/admin/quick?action=removePincode { storeId, pincode }
 * POST /api/admin/quick?action=setInventory { storeId, productId, variantId?, availableQty }
 * GET  /api/admin/quick?action=listStores
 */
@WebServlet("/api/admin/quick")
@RequiresRole("Admin")
public class AdminQuickController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AdminQuickController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final QuickStoreRepository repo = new QuickStoreRepository();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("listStores".equals(req.getParameter("action"))) {
            SendResponseUtil.sendResponse(new ApiResponse(true, "ok", repo.findAllActive(), 200), resp);
        } else {
            SendResponseUtil.sendResponse(new ApiResponse(false, "Use action=listStores", null, 400), resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getParameter("action");
        try {
            JsonNode b = MAPPER.readTree(req.getInputStream());
            switch (action == null ? "" : action) {
                case "createStore" -> createStore(b, resp);
                case "setRadius" -> {
                    boolean ok = repo.updateRadius(uuid(b, "storeId"), b.path("serviceRadiusKm").asDouble());
                    respond(ok, "radius updated", resp);
                }
                case "setActive" -> {
                    boolean ok = repo.setActive(uuid(b, "storeId"), b.path("active").asBoolean(true));
                    respond(ok, "store active flag updated", resp);
                }
                case "addPincode" -> {
                    boolean ok = repo.addPincode(uuid(b, "storeId"), b.path("pincode").asText());
                    respond(ok, "pincode allowed", resp);
                }
                case "removePincode" -> {
                    boolean ok = repo.removePincode(uuid(b, "storeId"), b.path("pincode").asText());
                    respond(ok, "pincode removed", resp);
                }
                case "setInventory" -> {
                    UUID variantId = b.hasNonNull("variantId") ? UUID.fromString(b.get("variantId").asText()) : null;
                    boolean ok = repo.upsertInventory(uuid(b, "storeId"), uuid(b, "productId"),
                            variantId, b.path("availableQty").asInt(0));
                    respond(ok, "inventory updated", resp);
                }
                default -> SendResponseUtil.sendResponse(
                        new ApiResponse(false, "unknown action", null, 400), resp);
            }
        } catch (Exception e) {
            LOG.error("admin quick error action={}", action, e);
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), resp);
        }
    }

    private void createStore(JsonNode b, HttpServletResponse resp) {
        QuickStore s = new QuickStore();
        s.setName(b.path("name").asText());
        s.setContactName(b.path("contactName").asText(null));
        s.setContactPhone(b.path("contactPhone").asText(null));
        s.setAddress(b.path("address").asText());
        s.setLat(b.path("lat").asDouble());
        s.setLng(b.path("lng").asDouble());
        s.setPincode(b.path("pincode").asText());
        s.setServiceRadiusKm(b.path("serviceRadiusKm").asDouble(5.0));
        s.setActive(true);
        QuickStore created = repo.create(s);
        if (created == null || created.getStoreId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not create store", null, 500), resp);
        } else {
            SendResponseUtil.sendResponse(new ApiResponse(true, "store created", created, 200), resp);
        }
    }

    private static UUID uuid(JsonNode b, String field) {
        return UUID.fromString(b.path(field).asText());
    }

    private void respond(boolean ok, String msg, HttpServletResponse resp) {
        SendResponseUtil.sendResponse(
                new ApiResponse(ok, ok ? msg : "operation failed", null, ok ? 200 : 400), resp);
    }
}
