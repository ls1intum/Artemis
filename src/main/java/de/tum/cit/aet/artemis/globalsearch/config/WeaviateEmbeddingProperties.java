package de.tum.cit.aet.artemis.globalsearch.config;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for the embeddings Artemis computes for global search through the OpenAI-compatible endpoint
 * ({@code artemis.weaviate.open-ai-base-url}).
 * <p>
 * Background work (the outbox writes of reconcile passes and of a rebuild) is paced so it can never overload the
 * embedding endpoint, the database or Weaviate; user searches and the writes caused by user actions are not limited by
 * these budgets and always go first.
 *
 * @param backgroundMaxRowsPerMinute     background index writes per minute, whether or not they need an embedding call
 * @param backgroundMaxRequestsPerMinute background embedding requests per minute
 * @param maxInputsPerRequest            chunks sent in one embedding request
 * @param queryTimeout                   time limit for the connection and for the response of the embedding request for a user
 *                                           search; when it is exceeded the search falls back to keyword search. Must be positive
 * @param writeTimeout                   time limit for the connection and for the response of an embedding request made while
 *                                           writing the index. Must be positive
 */
@Validated
@ConfigurationProperties(prefix = "artemis.weaviate.embedding", ignoreUnknownFields = false)
public record WeaviateEmbeddingProperties(@DefaultValue("600") @Positive int backgroundMaxRowsPerMinute, @DefaultValue("30") @Positive int backgroundMaxRequestsPerMinute,
        @DefaultValue("32") @Positive @Max(256) int maxInputsPerRequest, @DefaultValue("3s") Duration queryTimeout, @DefaultValue("60s") Duration writeTimeout) {

    /**
     * Rejects timeouts that are not positive: a zero read timeout would wait forever.
     */
    public WeaviateEmbeddingProperties {
        if (queryTimeout == null || !queryTimeout.isPositive() || writeTimeout == null || !writeTimeout.isPositive()) {
            throw new IllegalArgumentException("artemis.weaviate.embedding.query-timeout and write-timeout must be positive durations");
        }
    }
}
