package com.ecommerce.app.module.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsAnalysis;
import co.elastic.clients.json.JsonData;
import com.ecommerce.app.module.product.Product;
import com.ecommerce.app.module.product.ProductRepository;
import com.ecommerce.app.module.product.ProductImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Owns the {@code products_v1} index lifecycle - mapping creation, single
 * doc upsert/delete, and full bulk reindex from Postgres.
 *
 * <p>The index name carries a {@code _v1} suffix so a future mapping change
 * can be rolled out by creating {@code products_v2} and atomically
 * switching the {@code products} alias. We use the alias everywhere reads
 * happen so this is transparent to the search service.</p>
 *
 * <p>All public methods are best-effort: if the client is unavailable or
 * a call fails we log and return - never throw - so the underlying
 * product create/update/delete path is never broken by ES.</p>
 */
public final class ProductSearchIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(ProductSearchIndexer.class);

    public static final String INDEX = "products_v1";
    public static final String ALIAS = "products";

    private final ProductRepository productRepo;
    private final ProductImageRepository imageRepo;

    public ProductSearchIndexer() {
        this.productRepo = new ProductRepository();
        this.imageRepo = new ProductImageRepository();
    }

    // ----- bootstrap --------------------------------------------------------

    /** Idempotently create the index + alias. Safe to call on every startup. */
    public void ensureIndex() {
        ElasticsearchClient es = ElasticsearchConfig.client();
        if (es == null) return;
        try {
            boolean exists = es.indices().exists(b -> b.index(INDEX)).value();
            if (!exists) {
                es.indices().create(buildCreateRequest());
                LOG.info("Created Elasticsearch index {}", INDEX);
            }
            boolean aliasExists = es.indices().existsAlias(b -> b.name(ALIAS)).value();
            if (!aliasExists) {
                es.indices().putAlias(b -> b.index(INDEX).name(ALIAS));
                LOG.info("Pointed alias {} -> {}", ALIAS, INDEX);
            }
        } catch (Exception e) {
            LOG.warn("ensureIndex failed: {}", e.getMessage());
        }
    }

    private CreateIndexRequest buildCreateRequest() {
        // Custom analyzer:
        //   tokenize -> lowercase -> stop -> synonym (from synonyms.txt) -> stem
        // search_analyzer is the same minus edge_ngram so we don't match
        // every prefix in the world at query time.
        IndexSettingsAnalysis analysis = IndexSettingsAnalysis.of(a -> a
                .filter("product_synonym", f -> f
                        .definition(d -> d.synonym(s -> s
                                .synonyms(loadSynonyms())
                                .lenient(true))))
                .filter("product_edge_ngram", f -> f
                        .definition(d -> d.edgeNgram(e -> e
                                .minGram(2).maxGram(15))))
                .analyzer("product_index", an -> an
                        .custom(c -> c
                                .tokenizer("standard")
                                .filter(List.of(
                                        "lowercase", "asciifolding",
                                        "product_synonym", "stop", "snowball"))))
                .analyzer("product_search", an -> an
                        .custom(c -> c
                                .tokenizer("standard")
                                .filter(List.of(
                                        "lowercase", "asciifolding",
                                        "product_synonym", "stop", "snowball"))))
                .analyzer("product_autocomplete_index", an -> an
                        .custom(c -> c
                                .tokenizer("standard")
                                .filter(List.of(
                                        "lowercase", "asciifolding",
                                        "product_edge_ngram"))))
                .analyzer("product_autocomplete_search", an -> an
                        .custom(c -> c
                                .tokenizer("standard")
                                .filter(List.of("lowercase", "asciifolding"))))
        );

        TypeMapping mapping = TypeMapping.of(m -> m
                .properties("name", Property.of(p -> p.text(t -> t
                        .analyzer("product_index")
                        .searchAnalyzer("product_search")
                        .fields("autocomplete", Property.of(pp -> pp.text(tt -> tt
                                .analyzer("product_autocomplete_index")
                                .searchAnalyzer("product_autocomplete_search"))))
                        .fields("keyword", Property.of(pp -> pp.keyword(k -> k.ignoreAbove(256)))))))
                .properties("description", Property.of(p -> p.text(t -> t
                        .analyzer("product_index").searchAnalyzer("product_search"))))
                .properties("ingredients", Property.of(p -> p.text(t -> t
                        .analyzer("product_index").searchAnalyzer("product_search"))))
                .properties("category", Property.of(p -> p.keyword(k -> k)))
                .properties("price", Property.of(p -> p.double_(d -> d)))
                .properties("isActive", Property.of(p -> p.boolean_(b -> b)))
                .properties("stockQuantity", Property.of(p -> p.integer(i -> i)))
                .properties("primaryImageUrl", Property.of(p -> p.keyword(k -> k.index(false))))
                .properties("createdAt", Property.of(p -> p.date(d -> d)))
        );

        IndexSettings settings = IndexSettings.of(s -> s
                .numberOfShards("1")
                .numberOfReplicas("0")
                .analysis(analysis));

        return CreateIndexRequest.of(c -> c
                .index(INDEX)
                .settings(settings)
                .mappings(mapping));
    }

    private List<String> loadSynonyms() {
        List<String> out = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/search/synonyms.txt")) {
            if (is == null) return out;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
                }
            }
        } catch (Exception e) {
            LOG.warn("could not load synonyms.txt: {}", e.getMessage());
        }
        return out;
    }

    // ----- single-doc upsert / delete --------------------------------------

    /** Index (upsert) one product. Hydrates primary image URL if not set. */
    public void indexProduct(Product product) {
        ElasticsearchClient es = ElasticsearchConfig.client();
        if (es == null || product == null || product.getId() == null) return;
        try {
            Map<String, Object> doc = toDoc(product);
            es.index(i -> i
                    .index(ALIAS)
                    .id(product.getId().toString())
                    .document(doc)
                    .refresh(Refresh.False));
        } catch (Exception e) {
            LOG.warn("indexProduct {} failed: {}", product.getId(), e.getMessage());
        }
    }

    public void deleteProduct(UUID productId) {
        ElasticsearchClient es = ElasticsearchConfig.client();
        if (es == null || productId == null) return;
        try {
            es.delete(d -> d.index(ALIAS).id(productId.toString()).refresh(Refresh.False));
        } catch (Exception e) {
            LOG.warn("deleteProduct {} from ES failed: {}", productId, e.getMessage());
        }
    }

    // ----- full reindex (admin) --------------------------------------------

    /**
     * Pull every product + its primary image from Postgres and (re)index
     * into ES. Returns the number of documents indexed. Caller should
     * gate this behind admin auth - it walks the whole catalogue.
     */
    public int reindexAll() {
        ElasticsearchClient es = ElasticsearchConfig.client();
        if (es == null) return 0;
        ensureIndex();
        int total = 0;
        int pageSize = 200;
        int offset = 0;
        try {
            while (true) {
                List<Product> page = productRepo.findAllListView(pageSize, offset);
                if (page.isEmpty()) break;

                List<UUID> ids = new ArrayList<>(page.size());
                for (Product p : page) ids.add(p.getId());

                // findAllListView already populates primaryImageUrl, so no
                // extra batch lookup needed. Build the bulk request.
                List<BulkOperation> ops = new ArrayList<>(page.size());
                for (Product p : page) {
                    Map<String, Object> doc = toDoc(p);
                    ops.add(BulkOperation.of(b -> b.index(i -> i
                            .index(ALIAS)
                            .id(p.getId().toString())
                            .document(doc))));
                }
                BulkResponse resp = es.bulk(BulkRequest.of(b -> b
                        .operations(ops)
                        .refresh(Refresh.False)));
                if (resp.errors()) {
                    LOG.warn("bulk reindex page offset={} reported errors", offset);
                }
                total += page.size();
                if (page.size() < pageSize) break;
                offset += pageSize;
            }
            // make the newly indexed docs visible immediately
            es.indices().refresh(r -> r.index(ALIAS));
            LOG.info("reindexAll: indexed {} products into {}", total, ALIAS);
        } catch (Exception e) {
            LOG.warn("reindexAll failed at offset={}: {}", offset, e.getMessage());
        }
        return total;
    }

    // ----- helpers ----------------------------------------------------------

    private Map<String, Object> toDoc(Product p) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("name", p.getName());
        doc.put("description", p.getDescription());
        doc.put("ingredients", p.getIngredients());
        doc.put("category", p.getCategory());
        doc.put("price", p.getPrice());
        doc.put("isActive", p.isActive());
        doc.put("stockQuantity", p.getStockQuantity());
        // detail-page writes don't hit findAllListView, so fall back to
        // a targeted lookup if the field wasn't populated upstream.
        String img = p.getPrimaryImageUrl();
        if (img == null && p.getId() != null) {
            try {
                Map<UUID, String> m = imageRepo.findPrimaryUrlsByProductIds(List.of(p.getId()));
                img = m.get(p.getId());
            } catch (Exception ignore) { /* best effort */ }
        }
        doc.put("primaryImageUrl", img);
        if (p.getCreatedAt() != null) {
            doc.put("createdAt", p.getCreatedAt().toInstant().toString());
        }
        // suppress unused JsonData import warning - kept for future
        // structured-data fields (e.g. variants as nested).
        @SuppressWarnings("unused")
        JsonData reserved = null;
        return doc;
    }
}
