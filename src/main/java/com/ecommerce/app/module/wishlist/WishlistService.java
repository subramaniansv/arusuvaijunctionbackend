package com.ecommerce.app.module.wishlist;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin service layer over WishlistRepository.
 *
 * The repository already guards against duplicate inserts via the
 * composite PK and ON CONFLICT, so the service is mostly a place to
 * hold the user-scoped helpers we expose to the controller.
 */
public class WishlistService {
    private static final Logger LOG = LoggerFactory.getLogger(WishlistService.class);

    private final WishlistRepository repository = new WishlistRepository();

    public List<WishlistItem> list(UUID userId) {
        return repository.findAllForUser(userId);
    }

    public List<UUID> listProductIds(UUID userId) {
        return repository.findProductIds(userId);
    }

    public boolean add(UUID userId, UUID productId) {
        if (userId == null || productId == null) {
            throw new RuntimeException("productId is required");
        }
        boolean inserted = repository.add(userId, productId);
        LOG.info("wishlist add user={} product={} newRow={}", userId, productId, inserted);
        // Either way the product is now in the wishlist - we don't treat
        // a duplicate add as an error so the client can be naive.
        return true;
    }

    public boolean remove(UUID userId, UUID productId) {
        if (userId == null || productId == null) {
            throw new RuntimeException("productId is required");
        }
        return repository.remove(userId, productId);
    }

    public int count(UUID userId) {
        return repository.countForUser(userId);
    }
}
