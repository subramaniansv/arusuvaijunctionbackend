package com.ecommerce.app.module.iam.mapper;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ecommerce.app.module.iam.models.*;
public class PassWordResetConverterUtil {
    public static PasswordResetRequest requestToDto(HttpServletRequest request) {
    try {
        ObjectMapper mapper = new ObjectMapper();
          mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(request.getInputStream(), PasswordResetRequest.class);
    } catch (Exception e) {
        throw new RuntimeException("Failed to parse request JSON", e);
    }
}
}
