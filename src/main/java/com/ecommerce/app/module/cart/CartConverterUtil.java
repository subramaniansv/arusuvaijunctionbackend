package com.ecommerce.app.module.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

public class CartConverterUtil {
    private static final Logger LOG = LoggerFactory.getLogger(CartConverterUtil.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    public static CartItemRequest requestToItem(HttpServletRequest request) {
        try {
            JsonNode node = mapper.readTree(request.getInputStream());
            CartItemRequest dto = new CartItemRequest();
            if (node.hasNonNull("productId")) {
                dto.setProductId(UUID.fromString(node.get("productId").asText()));
            }
            if (node.hasNonNull("variantId")) {
                String raw = node.get("variantId").asText();
                if (raw != null && !raw.isEmpty()) {
                    dto.setVariantId(UUID.fromString(raw));
                }
            }
            if (node.hasNonNull("quantity")) {
                dto.setQuantity(node.get("quantity").asInt());
            }
            return dto;
        } catch (Exception e) {
            LOG.error("error parsing cart item request  ", e);
        }
        return null;
    }

    public static class CartItemRequest {
        private UUID productId;
        private UUID variantId;
        private int quantity;

        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public UUID getVariantId() { return variantId; }
        public void setVariantId(UUID variantId) { this.variantId = variantId; }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
