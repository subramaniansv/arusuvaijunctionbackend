package com.ecommerce.app.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SendResponseUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SendResponseUtil.class);

    public static void sendResponse(ApiResponse apiResponse ,HttpServletResponse response){


        try {

      ObjectMapper mapper = new ObjectMapper();
            response.getWriter().write(mapper.writeValueAsString(apiResponse));
            response.setStatus(apiResponse.getStatusCode());
            
        } catch (Exception e) {

            LOG.error("error ata send response util iam", e);
        }
    }
}