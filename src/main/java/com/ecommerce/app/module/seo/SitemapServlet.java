package com.ecommerce.app.module.seo;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.ecommerce.app.module.product.Product;
import com.ecommerce.app.module.product.ProductRepository;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Dynamic product sitemap.
 *
 * GET /arusuvai/sitemap-products.xml
 *   -> application/xml urlset listing every published product URL.
 *
 * The frontend's static /sitemap.xml covers Home, About, Contact, etc.
 * This one keeps product URLs discoverable as the catalogue grows
 * without rebuilding the site.
 *
 * Submit BOTH sitemaps in Google Search Console.
 *
 * Env:
 *   SITE_URL       public origin of the SPA (default https://www.arusuvaijunction.com)
 */
@WebServlet({"/sitemap-products.xml"})
public class SitemapServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(SitemapServlet.class);

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String siteUrl = stripTrailingSlash(
                first(ENVConfig.get("SITE_URL"), "https://www.arusuvaijunction.com"));

        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("application/xml; charset=UTF-8");
        res.setHeader("Cache-Control", "public, max-age=3600");

        try (PrintWriter w = res.getWriter()) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

            ProductRepository repo = new ProductRepository();
            int offset = 0;
            int pageSize = 500;
            while (true) {
                List<Product> page = repo.findAll(pageSize, offset);
                if (page == null || page.isEmpty()) break;
                for (Product p : page) {
                    UUID id = p.getId();
                    if (id == null) continue;
                    String lastmod = null;
                    if (p.getUpdatedAt() != null) {
                        lastmod = ISO.format(p.getUpdatedAt().toInstant());
                    } else if (p.getCreatedAt() != null) {
                        lastmod = ISO.format(p.getCreatedAt().toInstant());
                    } else {
                        lastmod = ISO.format(Instant.now());
                    }
                    w.write("  <url>\n");
                    w.write("    <loc>" + xml(siteUrl + "/products/" + id) + "</loc>\n");
                    w.write("    <lastmod>" + lastmod + "</lastmod>\n");
                    w.write("    <changefreq>weekly</changefreq>\n");
                    w.write("    <priority>0.7</priority>\n");
                    w.write("  </url>\n");
                }
                if (page.size() < pageSize) break;
                offset += pageSize;
            }

            w.write("</urlset>\n");
        } catch (Exception e) {
            LOG.warn("failed to build product sitemap: {}", e.getMessage());
            // Best-effort: response is already partially written; just close.
        }
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String first(String a, String b) {
        return (a == null || a.isBlank()) ? b : a;
    }

    private static String xml(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':  out.append("&amp;");  break;
                case '<':  out.append("&lt;");   break;
                case '>':  out.append("&gt;");   break;
                case '"':  out.append("&quot;"); break;
                case '\'': out.append("&apos;"); break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }
}
