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
            // IMPORTANT: declare charset BEFORE calling getWriter().
            // The Servlet spec defaults the response writer to
            // ISO-8859-1, which cannot encode characters outside Latin-1
            // (Tamil, Hindi, emoji, etc.) - they get silently replaced
            // with '?'. Once getWriter() is called the encoding is
            // locked, so we must set it first.
            response.setStatus(apiResponse.getStatusCode());
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(mapper.writeValueAsString(apiResponse));
            
        } catch (Exception e) {

            LOG.error("error ata send response util iam", e);
        }
    }
}