package com.ecommerce.app.security;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Forces UTF-8 for every inbound request body and every outbound response.
 *
 * <p>Why this exists — the Tamil mojibake bug</p>
 * <p>The Servlet spec says that when a request's {@code Content-Type} header
 * carries no explicit {@code charset} parameter, the container decodes the
 * body (and {@code application/x-www-form-urlencoded} / multipart parameters)
 * as <strong>ISO-8859-1</strong>. Browsers — and axios — send JSON as
 * {@code Content-Type: application/json} with NO charset, so any non-Latin-1
 * text (Tamil, Hindi, emoji) gets silently mangled the instant the body is
 * parsed. Crucially this parsing can be triggered by an upstream
 * {@code request.getParameter(...)} / {@code getParts()} call (e.g. in
 * AuthorizationFilter) long before the controller reads the stream, so fixing
 * it inside a single controller is not enough.</p>
 *
 * <p>Setting {@link ServletRequest#setCharacterEncoding(String)} here — in the
 * OUTERMOST filter, before anything touches the body — makes UTF-8 the decode
 * charset for the whole request. {@code setCharacterEncoding} only takes effect
 * if called before the body is read, which is exactly why this must run first.</p>
 *
 * <p>Note: if the client DID send an explicit charset, Tomcat has already
 * recorded it and {@code setCharacterEncoding} called here still overrides the
 * body decode — which is safe because JSON is UTF-8 by spec (RFC 8259) and all
 * our clients send UTF-8. We only override when the request didn't specify one,
 * to respect any legitimately non-UTF-8 client.</p>
 */
public class CharacterEncodingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // Only set when the client omitted a charset — don't stomp an explicit one.
        if (req.getCharacterEncoding() == null) {
            req.setCharacterEncoding("UTF-8");
        }
        // Default outbound charset too (controllers that use SendResponseUtil
        // already override this, but this covers any direct writer use).
        res.setCharacterEncoding("UTF-8");
        chain.doFilter(req, res);
    }
}
