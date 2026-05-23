package com.ecommerce.app.module.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.common.ApiResponse;
import com.ecommerce.app.util.SendResponseUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Public endpoint that returns the distinct list of product categories
 * currently in the catalogue. Used by the storefront filter sidebar so
 * the available categories stay in sync with the data (no hard-coded
 * client-side list).
 *
 *   GET /api/category  ->  ApiResponse { data: ["Beverages", "Millets", ...] }
 */
@WebServlet("/api/category")
public class CategoryController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(CategoryController.class);

    private final ProductService service = new ProductService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            List<String> categories = service.getDistinctCategories();
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "categories fetched", categories, 200),
                    response);
        } catch (Exception e) {
            LOG.error("exception fetching categories", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not fetch categories", null, 500),
                    response);
        }
    }
}
