package de.tum.cit.aet.artemis.globalsearch.config;

import static de.tum.cit.aet.artemis.core.config.ConfigurationValidator.HTTPS_SCHEME;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Weaviate integration.
 * Uses a Java record for immutable configuration.
 * <p>
 * When Weaviate is enabled, all properties must be explicitly configured.
 * Validation is performed in {@link WeaviateClientConfiguration} and {@link de.tum.cit.aet.artemis.core.config.ConfigurationValidator#validateWeaviateConfiguration}.
 *
 * @param enabled              whether Weaviate integration is enabled
 * @param httpHost             the Weaviate server HTTP host
 * @param httpPort             the Weaviate HTTP port
 * @param grpcPort             the Weaviate gRPC port
 * @param scheme               the HTTP scheme (http/https) - determines secure connection type
 * @param collectionPrefix     prefix prepended to all Weaviate collection names to avoid naming conflicts (e.g. with Pyris collections)
 * @param vectorizerModule     the vectorizer module to use: "none" for self-provided vectors, "text2vec-transformers" for automatic embeddings, "text2vec-openai" for
 *                                 OpenAI-compatible APIs (e.g. Ollama)
 * @param openAiEmbeddingModel the embedding model name (e.g. "qwen3-embedding:8b"), optional when using text2vec-openai; if omitted, the server's default model is used
 * @param openAiBaseUrl        the base URL for the OpenAI-compatible API (e.g. Ollama), required when using text2vec-openai
 * @param gpuApiKey            the API key for the OpenAI-compatible GPU server, required when using text2vec-openai (can be a dummy value for Ollama)
 * @param apiKey               the API key for authenticating against the Weaviate server itself (optional; when set, anonymous access should be disabled on the server)
 */
@ConfigurationProperties(prefix = "artemis.weaviate")
public record WeaviateConfigurationProperties(boolean enabled, String httpHost, @DefaultValue(DEFAULT_HTTP_PORT) int httpPort, @DefaultValue(DEFAULT_GRPC_PORT) int grpcPort,
        String scheme, @DefaultValue(DEFAULT_COLLECTION_PREFIX) String collectionPrefix, @DefaultValue(DEFAULT_VECTORIZER_MODULE) String vectorizerModule,
        String openAiEmbeddingModel, String openAiBaseUrl, String gpuApiKey, String apiKey) {

    public static final String DEFAULT_HTTP_PORT = "8001";

    public static final String DEFAULT_GRPC_PORT = "50051";

    public static final String DEFAULT_COLLECTION_PREFIX = "";

    /**
     * Compile-time constant for {@link DefaultValue}. Must match {@link SupportedVectorizer#NONE}.
     */
    public static final String DEFAULT_VECTORIZER_MODULE = "none";

    static {
        assert DEFAULT_VECTORIZER_MODULE.equals(SupportedVectorizer.NONE.configValue()) : "DEFAULT_VECTORIZER_MODULE must match SupportedVectorizer.NONE.configValue()";
    }

    /**
     * Returns whether secure connections should be used based on the scheme.
     *
     * @return true if scheme is "https", false otherwise
     */
    public boolean secure() {
        return HTTPS_SCHEME.equals(scheme);
    }
}
