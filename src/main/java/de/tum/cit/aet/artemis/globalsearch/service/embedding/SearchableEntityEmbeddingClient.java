package de.tum.cit.aet.artemis.globalsearch.service.embedding;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.globalsearch.config.SupportedVectorizer;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEmbeddingProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.embedding.EmbeddingRequestDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.embedding.EmbeddingResponseDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;

/**
 * Computes global search embeddings through the configured OpenAI-compatible endpoint
 * ({@code POST {open-ai-base-url}/v1/embeddings}, the same base URL convention Weaviate's OpenAI module uses).
 * <p>
 * Two clients with separate timeouts: a short one for user searches, which fall back to keyword search when it is
 * exceeded, and a longer one for index writes, which the outbox retries. Every response is validated before use: one
 * finite, non-zero vector of one dimension per input, in input order. Anything else is an
 * {@link EmbeddingFailedException}, so a write never stores a partial or invalid result.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityEmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    private static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Pattern TRAILING_SLASHES = Pattern.compile("/+$");

    private final WeaviateConfigurationProperties properties;

    private final WeaviateEmbeddingProperties embeddingProperties;

    private final RestClient queryClient;

    private final RestClient writeClient;

    public SearchableEntityEmbeddingClient(RestClient.Builder restClientBuilder, WeaviateConfigurationProperties properties, WeaviateEmbeddingProperties embeddingProperties) {
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
        this.queryClient = buildClient(restClientBuilder.clone(), embeddingProperties.queryTimeout());
        this.writeClient = buildClient(restClientBuilder.clone(), embeddingProperties.writeTimeout());
    }

    private RestClient buildClient(RestClient.Builder builder, Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        // The connection may not use more time than the response, so an unreachable endpoint fails a search as fast as a slow one.
        requestFactory.setConnectTimeout(readTimeout.compareTo(MAX_CONNECT_TIMEOUT) < 0 ? readTimeout : MAX_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(readTimeout);
        String baseUrl = properties.openAiBaseUrl() == null ? "" : TRAILING_SLASHES.matcher(properties.openAiBaseUrl()).replaceAll("");
        return builder.requestFactory(requestFactory).baseUrl(baseUrl).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.gpuApiKey()).build();
    }

    /**
     * Whether Artemis computes embeddings at all; with {@code vectorizer-module: none} global search is keyword-only.
     *
     * @return true when the OpenAI-compatible mode is configured
     */
    public boolean isEnabled() {
        return SupportedVectorizer.TEXT2VEC_OPENAI.configValue().equals(properties.vectorizerModule());
    }

    /**
     * The maximum number of texts sent in one request.
     *
     * @return the configured batch size
     */
    public int maxInputsPerRequest() {
        return embeddingProperties.maxInputsPerRequest();
    }

    /**
     * Embeds a user search query, prefixed with the configured query instruction, with the short query timeout.
     *
     * @param query the search query
     * @return the query vector
     * @throws EmbeddingFailedException if the request fails, times out or returns an invalid result
     */
    public float[] embedQuery(String query) {
        String instruction = properties.openAiQueryInstruction();
        String input = instruction == null || instruction.isEmpty() ? query : instruction + query;
        return embed(queryClient, List.of(input)).getFirst();
    }

    /**
     * Embeds texts for an index write in one request.
     *
     * @param texts at most {@link #maxInputsPerRequest()} texts
     * @return one vector per text, in the same order
     * @throws EmbeddingFailedException if the request fails or returns an invalid result
     */
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts.size() > embeddingProperties.maxInputsPerRequest()) {
            throw new IllegalArgumentException("At most " + embeddingProperties.maxInputsPerRequest() + " texts per request, got " + texts.size());
        }
        if (texts.isEmpty()) {
            return List.of();
        }
        return embed(writeClient, texts);
    }

    private List<float[]> embed(RestClient client, List<String> texts) {
        EmbeddingResponseDTO response;
        try {
            response = client.post().uri(EMBEDDINGS_PATH).contentType(MediaType.APPLICATION_JSON).body(new EmbeddingRequestDTO(properties.openAiEmbeddingModel(), texts)).retrieve()
                    .body(EmbeddingResponseDTO.class);
        }
        catch (RestClientException e) {
            throw new EmbeddingFailedException("Embedding request failed: " + e.getMessage(), e);
        }
        return validate(response, texts.size());
    }

    static List<float[]> validate(@Nullable EmbeddingResponseDTO response, int expectedCount) {
        if (response == null || response.data() == null || response.data().size() != expectedCount) {
            throw new EmbeddingFailedException("Expected " + expectedCount + " embeddings, got " + (response == null || response.data() == null ? 0 : response.data().size()));
        }
        float[][] vectors = new float[expectedCount][];
        for (EmbeddingResponseDTO.EmbeddingDataDTO item : response.data()) {
            if (item == null) {
                throw new EmbeddingFailedException("Empty entry in the embedding response");
            }
            if (item.index() < 0 || item.index() >= expectedCount || vectors[item.index()] != null) {
                throw new EmbeddingFailedException("Invalid or duplicate embedding index " + item.index());
            }
            vectors[item.index()] = requireValid(item.embedding());
        }
        int dimension = vectors[0].length;
        for (float[] vector : vectors) {
            if (vector.length != dimension) {
                throw new EmbeddingFailedException("Embeddings of one response differ in dimension: " + dimension + " and " + vector.length);
            }
        }
        return List.of(vectors);
    }

    private static float[] requireValid(@Nullable float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new EmbeddingFailedException("Empty embedding");
        }
        double squaredNorm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new EmbeddingFailedException("Embedding contains a non-finite value");
            }
            squaredNorm += (double) value * value;
        }
        if (squaredNorm == 0) {
            throw new EmbeddingFailedException("Embedding has zero norm");
        }
        return vector;
    }
}
