package com.ecommerce.app.module.iam.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.module.iam.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class SendResponseUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SendResponseUtil.class);

    public static void sendResponse(ApiResponse apiResponse ,HttpServletResponse response){


        try {

      ObjectMapper mapper = new ObjectMapper()
              .registerModule(new JavaTimeModule())
              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // IMPORTANT: declare charset BEFORE calling getWriter().
            // Servlet default is ISO-8859-1 which cannot encode Tamil/
            // Hindi/emoji - they get silently replaced with '?'.
            response.setStatus(apiResponse.getStatusCode());
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(mapper.writeValueAsString(apiResponse));
            
        } catch (Exception e) {

            LOG.error("error ata send response util iam ", e);
        }
    }
}
