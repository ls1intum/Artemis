package de.tum.cit.aet.artemis.globalsearch.config;

import java.util.Arrays;

import io.weaviate.client6.v1.api.collections.VectorConfig;

/**
 * Vectorizer modules supported by the Weaviate integration.
 * Each entry maps to a {@link VectorConfig.Kind} value used by the Weaviate client library.
 */
public enum SupportedVectorizer {

    NONE(VectorConfig.Kind.NONE), TEXT2VEC_TRANSFORMERS(VectorConfig.Kind.TEXT2VEC_TRANSFORMERS), TEXT2VEC_OPENAI(VectorConfig.Kind.TEXT2VEC_OPENAI);

    private final String configValue;

    SupportedVectorizer(VectorConfig.Kind kind) {
        this.configValue = kind.jsonValue();
    }

    /**
     * Returns the configuration string value as expected by Weaviate (e.g. "none", "text2vec-transformers").
     */
    public String configValue() {
        return configValue;
    }

    /**
     * Checks whether the given configuration value matches a supported vectorizer.
     *
     * @param value the vectorizer module string from configuration
     * @return {@code true} if the value matches any supported vectorizer
     */
    public static boolean isSupported(String value) {
        return Arrays.stream(values()).anyMatch(v -> v.configValue.equals(value));
    }
}
