package com.ecommerce.app.module.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ecommerce.app.module.product.image.MinIOStorageService;
import com.ecommerce.app.module.product.image.StorageFactory;
import com.ecommerce.app.module.product.image.StorageService;
import com.ecommerce.app.module.review.ReviewRepository;
import com.ecommerce.app.module.review.ReviewSummary;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.InputStream;

public class ProductService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductService.class);

    ProductRepository productRepository = new ProductRepository();
    ProductImageRepository productImageRepository = new ProductImageRepository();
    ProductVariantRepository productVariantRepository = new ProductVariantRepository();
    ReviewRepository reviewRepository = new ReviewRepository();
    StorageService storageService = StorageFactory.get();
    // ES indexer is best-effort: every call inside it is try/catched so
    // a down Elasticsearch never breaks a product write.
    com.ecommerce.app.module.search.ProductSearchIndexer searchIndexer =
            new com.ecommerce.app.module.search.ProductSearchIndexer();

    // Tiny in-memory cache for the unfiltered catalog list view. Catalog
    // pages are read-heavy and identical for every anonymous visitor, so
    // a 30s TTL avoids hammering Neon on every browse. Invalidated on any
    // product create/update/delete via invalidateListCache(). Filtered
    // /search results are NOT cached (high cardinality).
    private static final long LIST_CACHE_TTL_MS = 30_000L;
    private static final ConcurrentHashMap<String, CachedPage> LIST_CACHE = new ConcurrentHashMap<>();
    // Separate cache for category-filtered list views. Keyed by
    // sorted-categories|sort|limit|offset so equivalent filter sets share
    // an entry regardless of input order.
    private static final ConcurrentHashMap<String, CachedPage> CATEGORY_CACHE = new ConcurrentHashMap<>();

    private record CachedPage(long expiresAt, List<Product> products) {}

    private static void invalidateListCache() {
        LIST_CACHE.clear();
        CATEGORY_CACHE.clear();
    }

    public Product createProduct(Product product, List<Part> imageParts) {
        Product createdProduct = null;
        List<ProductImage> uploadedImg = new ArrayList<>();
        try {
            createdProduct = productRepository.create(product);
            if (createdProduct == null) {
                throw new RuntimeException("failed to create product");
            }
            for (int i = 0; i < imageParts.size(); i++) {
                LOG.info("entering img upload");
                Part imagePart = imageParts.get(i);
                String fileName = sanitizeFileName(imagePart.getSubmittedFileName());
                String objectKey;
                // try-with-resources: ensures the multipart temp-file stream is
                // closed even when MinIO upload throws. Without this, every
                // failed upload (e.g. MinIO down) leaks a file descriptor.
                try (InputStream inputStream = imagePart.getInputStream()) {
                    objectKey = storageService.upload(fileName, inputStream, imagePart.getSize(),
                            imagePart.getContentType());
                }
                String imgUrl = storageService.getFileUrl(objectKey);
                ProductImage productImage = new ProductImage();
                productImage.setProductId(createdProduct.getId());
                productImage.setObjectKey(objectKey);
                productImage.setImageUrl(imgUrl);
                productImage.setPrimary(i == 0);
                LOG.info("product img minio persisted");
                ProductImage savedImage = productImageRepository.create(productImage);
                LOG.info("product saved in img repo mysql");
                uploadedImg.add(savedImage);
            }
        } catch (Exception e) {
            LOG.error("exception during product creation", e);
            if (createdProduct != null) {
                productRepository.delete(createdProduct.getId());
            }
            return null;
        }
        LOG.info("product created in service {}", product.getName());
        invalidateListCache();
        // best-effort: index into Elasticsearch so /api/product/search
        // sees the new product. Hydrate the primary image URL first so
        // the indexed doc carries it.
        try {
            if (!uploadedImg.isEmpty()) {
                for (ProductImage img : uploadedImg) {
                    if (img.isPrimary()) { createdProduct.setPrimaryImageUrl(img.getImageUrl()); break; }
                }
            }
            searchIndexer.indexProduct(createdProduct);
        } catch (Exception e) { LOG.warn("ES index on create failed: {}", e.getMessage()); }
        return createdProduct;
    }

    // Get all products (catalog list view).
    //
    // Single Neon round-trip via LATERAL-join SQL in
    // ProductRepository.findAllListView. Cached for LIST_CACHE_TTL_MS so
    // repeat browsers don't even hit the DB.
    public List<Product> getAllProducts(int limit, int offset) {
        String key = limit + ":" + offset;
        long now = System.currentTimeMillis();
        CachedPage cached = LIST_CACHE.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.products();
        }
        List<Product> products = productRepository.findAllListView(limit, offset);
        LIST_CACHE.put(key, new CachedPage(now + LIST_CACHE_TTL_MS, products));
        return products;
    }

    // Get product with images
    public Product getProductById(UUID productId) {
        Product product = productRepository.findById(productId);
        if (product == null) {
            return null;
        }
        List<ProductImage> images = productImageRepository.findByProductId(productId);
        product.setImages(images);
        // Surface the primary image URL as a top-level field too so the
        // detail view matches the shape of list/search responses (single
        // primaryImageUrl). Walk the already-loaded list - no extra query.
        for (ProductImage img : images) {
            if (img.isPrimary()) {
                product.setPrimaryImageUrl(img.getImageUrl());
                break;
            }
        }
        // Hydrate review summary + first page of reviews so the product
        // detail view can render them in a single call.
        ReviewSummary summary = reviewRepository.summaryForProduct(productId);
        product.setAverageRating(summary.getAverageRating());
        product.setReviewCount(summary.getReviewCount());
        product.setReviews(reviewRepository.findByProductId(productId, 10, 0));
        // Hydrate purchasable variants (different sizes / pack counts).
        // Empty list when the product has no variants - the storefront
        // then falls back to product.price + product.stockQuantity.
        product.setVariants(productVariantRepository.findByProductId(productId));
        return product;
    }

    // Delete product with images
    public boolean deleteProduct(UUID productId) {
        try {
            List<ProductImage> images = productImageRepository.findByProductId(productId);
            // Delete files from storage
            for (ProductImage image : images) {
                storageService.delete(image.getObjectKey());
            }
            // Delete DB rows
            productImageRepository.deleteByProductId(productId);
            boolean ok = productRepository.delete(productId);
            if (ok) {
                invalidateListCache();
                try { searchIndexer.deleteProduct(productId); }
                catch (Exception e) { LOG.warn("ES delete failed: {}", e.getMessage()); }
            }
            return ok;

        } catch (Exception e) {
            LOG.error("exception at deleteProduct ", e);
            return false;
        }
    }

    //update product module just for fields not for img
    public Product updateProduct(Product product){
        Product updated = productRepository.update(product);
        if (updated != null) {
            invalidateListCache();
            try { searchIndexer.indexProduct(updated); }
            catch (Exception e) { LOG.warn("ES index on update failed: {}", e.getMessage()); }
        }
        return updated;
    }

    // ------------------------------------------------------------------
    // Image management (admin).
    //
    // {@link #addImage} uploads a new file to object storage and inserts
    // a row in product_images. The first image to ever land on a product
    // is automatically marked primary. {@link #deleteImage} removes the
    // R2 object and the DB row; if the deleted image was primary and
    // other images exist, the first remaining row is promoted so the
    // product never ends up without a primary thumbnail.
    // ------------------------------------------------------------------

    public ProductImage addImage(UUID productId, Part imagePart) {
        if (imagePart == null || imagePart.getSize() <= 0) return null;
        Product product = productRepository.findById(productId);
        if (product == null) return null;
        try {
            String fileName = sanitizeFileName(imagePart.getSubmittedFileName());
            String objectKey;
            try (InputStream in = imagePart.getInputStream()) {
                objectKey = storageService.upload(fileName, in,
                        imagePart.getSize(), imagePart.getContentType());
            }
            String imgUrl = storageService.getFileUrl(objectKey);
            // If the product currently has no images, this one becomes
            // primary; otherwise it joins the gallery as a secondary.
            boolean primary = productImageRepository.findByProductId(productId).isEmpty();
            ProductImage img = new ProductImage();
            img.setProductId(productId);
            img.setObjectKey(objectKey);
            img.setImageUrl(imgUrl);
            img.setPrimary(primary);
            ProductImage saved = productImageRepository.create(img);
            invalidateListCache();
            try { searchIndexer.indexProduct(getProductById(productId)); }
            catch (Exception e) { LOG.warn("ES re-index after image add failed: {}", e.getMessage()); }
            return saved;
        } catch (Exception e) {
            LOG.error("addImage failed", e);
            return null;
        }
    }

    public boolean deleteImage(UUID imageId) {
        ProductImage img = productImageRepository.findById(imageId);
        if (img == null) return false;
        // Drop the object first so a successful DB delete is never
        // shadowed by an orphan key in R2. Storage failures are logged
        // but should not block the DB delete: the admin's intent is to
        // remove the image, and a stale object is recoverable.
        try { storageService.delete(img.getObjectKey()); }
        catch (Exception e) { LOG.warn("storage delete failed for {}: {}", img.getObjectKey(), e.getMessage()); }
        boolean ok = productImageRepository.deleteById(imageId);
        if (ok && img.isPrimary()) {
            // promote any remaining image to primary so list views still
            // get a thumbnail
            List<ProductImage> rest = productImageRepository.findByProductId(img.getProductId());
            if (!rest.isEmpty()) {
                productImageRepository.setPrimary(img.getProductId(), rest.get(0).getId());
            }
        }
        if (ok) {
            invalidateListCache();
            try { searchIndexer.indexProduct(getProductById(img.getProductId())); }
            catch (Exception e) { LOG.warn("ES re-index after image delete failed: {}", e.getMessage()); }
        }
        return ok;
    }

    public boolean setPrimaryImage(UUID productId, UUID imageId) {
        boolean ok = productImageRepository.setPrimary(productId, imageId);
        if (ok) {
            invalidateListCache();
            try { searchIndexer.indexProduct(getProductById(productId)); }
            catch (Exception e) { LOG.warn("ES re-index after primary swap failed: {}", e.getMessage()); }
        }
        return ok;
    }

    // ------------------------------------------------------------------
    // Search facade.
    //
    // Delegates the actual SQL to ProductRepository.search and then
    // hydrates each product with its images. Filters/sort are all optional;
    // see ProductRepository.search for the contract.
    // ------------------------------------------------------------------
    public List<String> getDistinctCategories() {
        return productRepository.findDistinctCategories();
    }

    // Fast path for category-only filters (no keyword search, no price
    // bounds, no inStock toggle). Uses the single-query LATERAL-join
    // repo method plus a TTL cache, so it does not pay the 3-round-trip
    // tax of the generic search() path. Full-text search will move to
    // Elasticsearch later and is intentionally untouched here.
    public List<Product> getProductsByCategories(
            List<String> categories, String sort, int limit, int offset) {

        // Normalise key: lower-case + sorted + de-duped so {Sweets, Snacks}
        // and {snacks, sweets} share one cache entry.
        java.util.TreeSet<String> norm = new java.util.TreeSet<>();
        for (String c : categories) {
            if (c != null && !c.trim().isEmpty()) norm.add(c.trim());
        }
        String key = String.join(",", norm) + "|" + (sort == null ? "" : sort) + "|" + limit + ":" + offset;
        long now = System.currentTimeMillis();
        CachedPage cached = CATEGORY_CACHE.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.products();
        }
        List<Product> products = productRepository.findByCategoriesListView(
                new ArrayList<>(norm), sort, limit, offset);
        CATEGORY_CACHE.put(key, new CachedPage(now + LIST_CACHE_TTL_MS, products));
        return products;
    }

    public List<Product> searchProducts(
            String q,
            List<String> categories,
            Double minPrice,
            Double maxPrice,
            boolean inStock,
            String sort,
            int limit,
            int offset
    ) {
        List<Product> products = productRepository.search(
                q, categories, minPrice, maxPrice, inStock, sort, limit, offset);
        if (products.isEmpty()) {
            return products;
        }
        // Batch lookup of primary image URLs so search results carry a
        // thumbnail without an N+1 round-trip. Full images list is only
        // returned by the detail endpoint.
        List<UUID> ids = new ArrayList<>(products.size());
        for (Product p : products) {
            ids.add(p.getId());
        }
        Map<UUID, String> primaryUrls = productImageRepository.findPrimaryUrlsByProductIds(ids);
        Map<UUID, ReviewSummary> summaries = reviewRepository.summariesForProducts(ids);
        for (Product p : products) {
            p.setPrimaryImageUrl(primaryUrls.get(p.getId()));
            ReviewSummary s = summaries.getOrDefault(p.getId(), new ReviewSummary(0.0, 0));
            p.setAverageRating(s.getAverageRating());
            p.setReviewCount(s.getReviewCount());
        }
        return products;
    }

    /**
     * Normalise an uploaded filename so it produces a URL-safe object key.
     *
     * macOS screenshot filenames contain a U+202F narrow-no-break-space
     * before AM/PM, which Tomcat's multipart parser maps to '?' (Latin-1
     * replacement for an unmapped UTF-8 byte). When that key gets embedded
     * in the public R2 URL, '?' starts the query string and the browser
     * requests an object that doesn't exist - the image silently 404s.
     *
     * Strategy: strip directory traversal, replace any character outside
     * [A-Za-z0-9._-] with '_', collapse runs, and keep the extension.
     */
    private static String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "file";
        // strip any path component a browser might leak (IE6 used to send full paths)
        int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        String name = slash >= 0 ? raw.substring(slash + 1) : raw;
        // replace every URL-unsafe / non-printable char with '_'
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        // collapse repeated underscores and trim leading/trailing ones
        cleaned = cleaned.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (cleaned.isEmpty()) cleaned = "file";
        return cleaned;
    }

}
