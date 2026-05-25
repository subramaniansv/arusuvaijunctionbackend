package com.ecommerce.app.module.address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Address management for the authenticated user.
 *
 *   GET    /api/address               list all saved addresses
 *   POST   /api/address               create a new address
 *   PUT    /api/address?id=<uuid>     update an existing address
 *   DELETE /api/address?id=<uuid>     delete an address
 *
 * All operations are scoped to the caller's userId from the JWT
 * so users can only access their own addresses.
 *
 * Request body (POST / PUT):
 * {
 *   "label":    "Home",        // optional
 *   "fullName": "...",
 *   "phone":    "...",
 *   "line1":    "...",
 *   "line2":    "...",         // optional
 *   "city":     "...",
 *   "state":    "...",
 *   "pincode":  "...",
 *   "country":  "IN",
 *   "isDefault": true          // optional, defaults to false
 * }
 */
@WebServlet("/api/address")
public class UserAddressController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(UserAddressController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final UserAddressService service = new UserAddressService();

    // ------------------------------------------------------------------ GET
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unauthenticated", null, 401), res);
            return;
        }
        try {
            List<UserAddress> addresses = service.getAddresses(caller.getUserId());
            SendResponseUtil.sendResponse(new ApiResponse(true, "addresses fetched", addresses, 200), res);
        } catch (Exception e) {
            LOG.error("GET /api/address error", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not fetch addresses", null, 500), res);
        }
    }

    // ----------------------------------------------------------------- POST
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unauthenticated", null, 401), res);
            return;
        }
        try {
            UserAddress input = parseBody(req);
            if (input == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "invalid request body", null, 400), res);
                return;
            }
            UserAddress created = service.createAddress(caller.getUserId(), input);
            if (created == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "could not save address", null, 500), res);
                return;
            }
            SendResponseUtil.sendResponse(new ApiResponse(true, "address saved", created, 201), res);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), res);
        } catch (Exception e) {
            LOG.error("POST /api/address error", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not save address", null, 500), res);
        }
    }

    // ------------------------------------------------------------------ PUT
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unauthenticated", null, 401), res);
            return;
        }
        String idParam = req.getParameter("id");
        if (idParam == null || idParam.isBlank()) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing ?id= param", null, 400), res);
            return;
        }
        UUID addressId;
        try {
            addressId = UUID.fromString(idParam);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid address id", null, 400), res);
            return;
        }
        try {
            UserAddress input = parseBody(req);
            if (input == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "invalid request body", null, 400), res);
                return;
            }
            UserAddress updated = service.updateAddress(caller.getUserId(), addressId, input);
            SendResponseUtil.sendResponse(new ApiResponse(true, "address updated", updated, 200), res);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), res);
        } catch (Exception e) {
            LOG.error("PUT /api/address error", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not update address", null, 500), res);
        }
    }

    // --------------------------------------------------------------- DELETE
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unauthenticated", null, 401), res);
            return;
        }
        String idParam = req.getParameter("id");
        if (idParam == null || idParam.isBlank()) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing ?id= param", null, 400), res);
            return;
        }
        UUID addressId;
        try {
            addressId = UUID.fromString(idParam);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid address id", null, 400), res);
            return;
        }
        try {
            service.deleteAddress(caller.getUserId(), addressId);
            SendResponseUtil.sendResponse(new ApiResponse(true, "address deleted", null, 200), res);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 404), res);
        } catch (Exception e) {
            LOG.error("DELETE /api/address error", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not delete address", null, 500), res);
        }
    }

    // ---------------------------------------------------------- body parser
    private UserAddress parseBody(HttpServletRequest req) {
        try {
            JsonNode body = MAPPER.readTree(req.getInputStream());
            if (body == null) return null;
            UserAddress a = new UserAddress();
            if (body.hasNonNull("label"))     a.setLabel(body.get("label").asText());
            if (body.hasNonNull("fullName"))  a.setFullName(body.get("fullName").asText());
            if (body.hasNonNull("phone"))     a.setPhone(body.get("phone").asText());
            if (body.hasNonNull("line1"))     a.setLine1(body.get("line1").asText());
            if (body.hasNonNull("line2"))     a.setLine2(body.get("line2").asText());
            if (body.hasNonNull("city"))      a.setCity(body.get("city").asText());
            if (body.hasNonNull("state"))     a.setState(body.get("state").asText());
            if (body.hasNonNull("pincode"))   a.setPincode(body.get("pincode").asText());
            if (body.hasNonNull("country"))   a.setCountry(body.get("country").asText());
            if (body.hasNonNull("isDefault")) a.setDefault(body.get("isDefault").asBoolean(false));
            return a;
        } catch (Exception e) {
            LOG.warn("parseBody failed", e);
            return null;
        }
    }
}
