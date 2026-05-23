package com.ecommerce.app.module.search;

import com.ecommerce.app.common.ApiResponse;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.util.SendResponseUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Dedicated search endpoints. Lives separately from {@link
 * com.ecommerce.app.module.product.ProductController} so the existing
 * {@code GET /api/product?q=...} behaviour stays exactly as-is until
 * we decide to retire it.
 *
 * <p>Routes (servlet mounted on {@code /api/product/search/*}):</p>
 * <ul>
 *   <li>{@code GET  /api/product/search?q=&limit=&offset=}        - relevance + fuzzy + highlights</li>
 *   <li>{@code GET  /api/product/search/suggest?q=&limit=}         - autocomplete (edge n-grams)</li>
 *   <li>{@code POST /api/product/search/reindex} (admin)           - rebuild the index from Postgres</li>
 * </ul>
 */
public class ProductSearchController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ProductSearchController.class);

    private final ProductSearchService service = new ProductSearchService();
    private final ProductSearchIndexer indexer = new ProductSearchIndexer();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String sub = subPath(req);
            if ("/suggest".equals(sub)) {
                String q = req.getParameter("q");
                int limit = parseInt(req.getParameter("limit"), 8);
                Map<String, Object> out = service.suggest(q, limit);
                SendResponseUtil.sendResponse(new ApiResponse(true, "suggestions", out, 200), resp);
                return;
            }
            if (sub.isEmpty() || "/".equals(sub)) {
                String q = req.getParameter("q");
                int limit = parseInt(req.getParameter("limit"), 10);
                int offset = parseInt(req.getParameter("offset"), 0);
                if (limit <= 0 || limit > 100) limit = 10;
                if (offset < 0) offset = 0;
                Map<String, Object> out = service.search(q, limit, offset);
                SendResponseUtil.sendResponse(new ApiResponse(true, "search results", out, 200), resp);
                return;
            }
            SendResponseUtil.sendResponse(new ApiResponse(false, "not found", null, 404), resp);
        } catch (Exception e) {
            LOG.error("search exception", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "search failed", e.getMessage(), 500), resp);
        }
    }

    @Override
    @RequiresRole("Admin")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            String sub = subPath(req);
            if (!"/reindex".equals(sub)) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "not found", null, 404), resp);
                return;
            }
            int n = indexer.reindexAll();
            Map<String, Object> body = Map.of("indexed", n);
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "reindex complete", body, 200), resp);
        } catch (Exception e) {
            LOG.error("reindex exception", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "reindex failed", e.getMessage(), 500), resp);
        }
    }

    private static String subPath(HttpServletRequest req) {
        String pi = req.getPathInfo();
        return pi == null ? "" : pi;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try { return Integer.parseInt(raw); } catch (NumberFormatException nfe) { return fallback; }
    }
}
