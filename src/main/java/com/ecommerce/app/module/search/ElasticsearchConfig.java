package com.ecommerce.app.module.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Lazy singleton holder for the Elasticsearch client.
 *
 * <p>Configuration via env vars (all optional - default to local dev):</p>
 * <ul>
 *   <li>{@code ELASTICSEARCH_URL}      - full URL, default {@code http://localhost:9200}</li>
 *   <li>{@code ELASTICSEARCH_API_KEY}  - base64 api-key for Elastic Cloud (Authorization: ApiKey ...)</li>
 *   <li>{@code ELASTICSEARCH_USERNAME} / {@code ELASTICSEARCH_PASSWORD} - basic auth fallback</li>
 * </ul>
 *
 * <p>If construction fails (ES not reachable, bad config) every accessor
 * returns {@code null} so callers can degrade gracefully back to the
 * Postgres-backed search path.</p>
 */
public final class ElasticsearchConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchConfig.class);

    private static volatile ElasticsearchClient CLIENT;
    private static volatile boolean INIT_TRIED;

    private ElasticsearchConfig() {}

    public static ElasticsearchClient client() {
        if (CLIENT != null) return CLIENT;
        if (INIT_TRIED) return null; // already failed once; don't keep retrying on every request
        synchronized (ElasticsearchConfig.class) {
            if (CLIENT != null) return CLIENT;
            if (INIT_TRIED) return null;
            INIT_TRIED = true;
            try {
                CLIENT = build();
                LOG.info("Elasticsearch client initialised: {}", url());
            } catch (Exception e) {
                LOG.warn("Elasticsearch client could not be initialised ({}). " +
                        "Search will fall back to Postgres until ES is reachable.", e.getMessage());
                CLIENT = null;
            }
        }
        return CLIENT;
    }

    /** Force a re-init on next call (useful after starting ES in dev). */
    public static synchronized void reset() {
        CLIENT = null;
        INIT_TRIED = false;
    }

    private static String url() {
        String raw = System.getenv("ELASTICSEARCH_URL");
        return (raw == null || raw.isBlank()) ? "http://localhost:9200" : raw.trim();
    }

    private static ElasticsearchClient build() {
        URI uri = URI.create(url());
        int port = uri.getPort() == -1
                ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)
                : uri.getPort();
        HttpHost host = new HttpHost(uri.getHost(), port, uri.getScheme());

        RestClientBuilder builder = RestClient.builder(host)
                .setRequestConfigCallback(rc -> rc
                        .setConnectTimeout(2_000)
                        .setSocketTimeout(10_000));

        String apiKey = System.getenv("ELASTICSEARCH_API_KEY");
        String user = System.getenv("ELASTICSEARCH_USERNAME");
        String pass = System.getenv("ELASTICSEARCH_PASSWORD");
        if (apiKey != null && !apiKey.isBlank()) {
            Header[] headers = { new BasicHeader("Authorization", "ApiKey " + apiKey) };
            builder.setDefaultHeaders(headers);
        } else if (user != null && pass != null && !user.isBlank()) {
            String encoded = java.util.Base64.getEncoder()
                    .encodeToString((user + ":" + pass).getBytes());
            Header[] headers = { new BasicHeader("Authorization", "Basic " + encoded) };
            builder.setDefaultHeaders(headers);
        }

        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
