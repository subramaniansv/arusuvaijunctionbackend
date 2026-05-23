package com.ecommerce.app.module.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ProductConveterUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ProductConveterUtil.class);

    static ObjectMapper objectMapper = new ObjectMapper();

    public static Product requestToDto(HttpServletRequest request) {
        try {
            objectMapper.registerModule(new JavaTimeModule());
            return objectMapper.readValue(request.getInputStream(), Product.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse request JSON", e);
        }
    }

    public static Product stringtoDto(String json) {
        try {
        Product product = objectMapper.readValue(json, Product.class);
        return product;       
        } catch (Exception e) {
            // TODO: handle exception
            LOG.error("exception", e);
        }
        return null;
    }
}
