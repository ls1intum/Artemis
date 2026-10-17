package de.tum.cit.aet.artemis.globalsearch.config;

import java.util.Arrays;

/**
 * Embedding modes of global search, selected by {@code artemis.weaviate.vectorizer-module}.
 * <ul>
 * <li>{@code none}: keyword (BM25) search only, no vectors.</li>
 * <li>{@code text2vec-openai}: Artemis computes one vector per chunk of every entity through an OpenAI-compatible
 * embedding endpoint ({@code open-ai-base-url}, {@code open-ai-embedding-model}, {@code gpu-api-key}) and searches
 * hybrid. The value keeps the name of the Weaviate module that used to compute the vectors, so existing
 * configurations stay valid.</li>
 * </ul>
 */
public enum SupportedVectorizer {

    NONE("none"), TEXT2VEC_OPENAI("text2vec-openai");

    /**
     * The former mode in which Weaviate computed vectors with a local transformers container; no longer supported.
     */
    public static final String REMOVED_TEXT2VEC_TRANSFORMERS = "text2vec-transformers";

    private final String configValue;

    SupportedVectorizer(String configValue) {
        this.configValue = configValue;
    }

    /**
     * Returns the configuration value (e.g. "none", "text2vec-openai").
     *
     * @return the config value string
     */
    public String configValue() {
        return configValue;
    }

    /**
     * Checks whether the given configuration value matches a supported mode.
     *
     * @param value the vectorizer module string from configuration
     * @return {@code true} if the value matches a supported mode
     */
    public static boolean isSupported(String value) {
        return Arrays.stream(values()).anyMatch(vectorizer -> vectorizer.configValue.equals(value));
    }
}
