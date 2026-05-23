package com.ecommerce.app.module.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.json.JsonData;
import com.ecommerce.app.module.product.ProductService;
import com.ecommerce.app.module.product.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Read-side facade around the {@code products} ES alias.
 *
 * <p>Public API:</p>
 * <ul>
 *   <li>{@link #search(String, int, int)} - relevance-ranked, typo-tolerant
 *       full-text search with highlights. Falls back to the existing
 *       Postgres {@code ProductService.searchProducts} when ES is down.</li>
 *   <li>{@link #suggest(String, int)} - search-as-you-type via the
 *       {@code name.autocomplete} subfield (edge n-grams).</li>
 * </ul>
 *
 * <p>Result shape is intentionally lean - id, name, description,
 * category, price, stockQuantity, isActive, primaryImageUrl, score,
 * highlights. Reviews live in Postgres and are not duplicated here.</p>
 */
public final class ProductSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductSearchService.class);

    private final ProductService legacy = new ProductService();

    public Map<String, Object> search(String q, int limit, int offset) {
        ElasticsearchClient es = ElasticsearchConfig.client();
        if (es == null || q == null || q.trim().isEmpty()) {
            return fallback(q, limit, offset, "elasticsearch-unavailable");
        }
        String query = q.trim();

        try {
            // multi_match best_fields with per-field boosts. Fuzziness
            // AUTO gives typo tolerance scaled to term length.
            Query nameDescIng = Query.of(qb -> qb.multiMatch(m -> m
                    .query(query)
                    .fields("name^3", "category^2", "ingredients^1.5", "description")
                    .type(TextQueryType.BestFields)
                    .fuzziness("AUTO")
                    .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.Or)
                    .minimumShouldMatch("1")));

            // Phrase boost - if the user typed multiple words that appear
            // together, surface those products first.
            Query phrase = Query.of(qb -> qb.multiMatch(m -> m
                    .query(query)
                    .fields("name^3", "description")
                    .type(TextQueryType.Phrase)
                    .boost(2.0f)));

            Query active = Query.of(qb -> qb.term(t -> t.field("isActive").value(true)));

            Query bool = Query.of(qb -> qb.bool(b -> b
                    .must(active)
                    .should(nameDescIng)
                    .should(phrase)
                    .minimumShouldMatch("1")));

            Highlight hl = Highlight.of(h -> h
                    .preTags("<mark>").postTags("</mark>")
                    .fields("name", HighlightField.of(f -> f.numberOfFragments(0)))
                    .fields("description", HighlightField.of(f -> f
                            .fragmentSize(120).numberOfFragments(1)))
                    .fields("ingredients", HighlightField.of(f -> f
                            .fragmentSize(120).numberOfFragments(1))));

            @SuppressWarnings("rawtypes")
            SearchResponse<Map> resp = es.search(SearchRequest.of(r -> r
                    .index(ProductSearchIndexer.ALIAS)
                    .query(bool)
                    .highlight(hl)
                    .from(Math.max(0, offset))
                    .size(Math.max(1, Math.min(100, limit)))), Map.class);

            List<Map<String, Object>> results = new ArrayList<>(resp.hits().hits().size());
            for (@SuppressWarnings("rawtypes") Hit<Map> hit : resp.hits().hits()) {
                results.add(toResult(hit));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("source", "elasticsearch");
            out.put("query", query);
            long total = (resp.hits().total() != null) ? resp.hits().total().value() : results.size();
            out.put("total", total);
            out.put("results", results);
            return out;
        } catch (Exception e) {
            LOG.warn("ES search failed for q='{}': {}", query, e.getMessage());
            return fallback(query, limit, offset, "elasticsearch-error");
        }
    }

    public Map<String, Object> suggest(String prefix, int limit) {
        ElasticsearchClient es = ElasticsearchConfig.client();
        if (es == null || prefix == null || prefix.trim().isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("source", es == null ? "elasticsearch-unavailable" : "elasticsearch");
            empty.put("query", prefix == null ? "" : prefix);
            empty.put("results", List.of());
            return empty;
        }
        String p = prefix.trim();
        try {
            Query q = Query.of(qb -> qb.bool(b -> b
                    .must(Query.of(mq -> mq.term(t -> t.field("isActive").value(true))))
                    .must(Query.of(mq -> mq.match(m -> m
                            .field("name.autocomplete")
                            .query(p))))));
            @SuppressWarnings("rawtypes")
            SearchResponse<Map> resp = es.search(SearchRequest.of(r -> r
                    .index(ProductSearchIndexer.ALIAS)
                    .query(q)
                    .source(s -> s.filter(f -> f.includes("name", "category", "price", "primaryImageUrl")))
                    .size(Math.max(1, Math.min(20, limit)))), Map.class);

            List<Map<String, Object>> results = new ArrayList<>();
            for (@SuppressWarnings("rawtypes") Hit<Map> hit : resp.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> src = hit.source();
                if (src == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", hit.id());
                row.put("name", src.get("name"));
                row.put("category", src.get("category"));
                row.put("price", src.get("price"));
                row.put("primaryImageUrl", src.get("primaryImageUrl"));
                results.add(row);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("source", "elasticsearch");
            out.put("query", p);
            out.put("results", results);
            return out;
        } catch (Exception e) {
            LOG.warn("ES suggest failed for prefix='{}': {}", p, e.getMessage());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("source", "elasticsearch-error");
            out.put("query", p);
            out.put("results", List.of());
            return out;
        }
    }

    // ----- helpers ---------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> toResult(Hit<Map> hit) {
        Map<String, Object> src = hit.source();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", hit.id());
        row.put("score", hit.score());
        if (src != null) {
            row.put("name", src.get("name"));
            row.put("description", src.get("description"));
            row.put("category", src.get("category"));
            row.put("price", src.get("price"));
            row.put("stockQuantity", src.get("stockQuantity"));
            row.put("isActive", src.get("isActive"));
            row.put("primaryImageUrl", src.get("primaryImageUrl"));
        }
        if (hit.highlight() != null && !hit.highlight().isEmpty()) {
            row.put("highlights", hit.highlight());
        }
        // suppress unused-import warning - JsonData is reserved for
        // future runtime-typed fields.
        @SuppressWarnings("unused") JsonData reserved = null;
        return row;
    }

    private Map<String, Object> fallback(String q, int limit, int offset, String reason) {
        // The legacy SQL search is left fully intact (will be replaced by
        // ES once stable). We only delegate to it when ES is offline so
        // the storefront never loses search.
        List<Product> products = legacy.searchProducts(
                q, null, null, null, false, null, limit, offset);
        List<Map<String, Object>> results = new ArrayList<>(products.size());
        for (Product p : products) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("description", p.getDescription());
            row.put("category", p.getCategory());
            row.put("price", p.getPrice());
            row.put("stockQuantity", p.getStockQuantity());
            row.put("isActive", p.isActive());
            row.put("primaryImageUrl", p.getPrimaryImageUrl());
            results.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "postgres-fallback");
        out.put("reason", reason);
        out.put("query", q == null ? "" : q);
        out.put("total", results.size());
        out.put("results", results);
        return out;
    }
}
