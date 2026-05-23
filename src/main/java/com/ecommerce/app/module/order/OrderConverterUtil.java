package com.ecommerce.app.module.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
public class OrderConverterUtil {
    private static final Logger LOG = LoggerFactory.getLogger(OrderConverterUtil.class);

    static ObjectMapper mapper = new ObjectMapper();
    public static Order requestToDto(HttpServletRequest request){
        try {
            return mapper.readValue(request.getInputStream(), Order.class);
        } catch (Exception e) {
            LOG.error("exception", e);
            // TODO: handle exception
        }
        return null;
    }   
}
