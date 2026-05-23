package com.ecommerce.app.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

/**
 * Outermost servlet filter: catches anything thrown by the rest of the chain
 * (other filters, servlets, business code) and returns a uniform JSON 500
 * envelope so stack traces never reach the client.
 *
 * Filter ordering note
 * --------------------
 * With pure annotations (no web.xml), Tomcat sorts filters for the same
 * url-pattern by filterName alphabetically. We pin this filter's name to
 * "0_GlobalExceptionFilter" and AuthorizationFilter's to
 * "1_AuthorizationFilter" so this one always runs FIRST in the chain, and
 * therefore catches exceptions from every other filter and servlet below it.
 *
 * Behaviour
 * ---------
 * - chain.doFilter is executed normally on the happy path; this filter is
 *   transparent.
 * - On any Throwable from below: logs server-side, sends a generic 500 JSON
 *   envelope. The exception message is NOT echoed to the client to avoid
 *   leaking SQL strings, file paths, internal class names, etc.
 * - If the response is already committed (controller already wrote the
 *   body before throwing), the filter cannot rewrite it -- it just logs
 *   and lets the partial response go out.
 */
@WebFilter(filterName = "0_GlobalExceptionFilter", urlPatterns = "/*")
public class GlobalExceptionFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionFilter.class);


    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        try {
            chain.doFilter(req, res);
        } catch (Throwable t) {
            // Log the FULL exception server-side so we can debug.
            LOG.error("unhandled exception caught by GlobalExceptionFilter: ", t);

            // If a downstream component already started writing the response
            // we cannot safely overwrite headers/body. Best effort: bail.
            if (response.isCommitted()) {
                return;
            }

            // Generic envelope; never echo the exception message.
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "internal server error", null, 500),
                    response);
        }
    }
}
