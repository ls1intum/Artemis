package de.tum.cit.aet.artemis.globalsearch.service.embedding;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.openai.errors.OpenAIException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.globalsearch.config.SupportedVectorizer;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEmbeddingProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.embedding.EmbeddingResponseDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;

/**
 * Computes global search embeddings with the preset assigned in {@code embedding-llm-preset}, through {@link LlmClientService}.
 * <p>
 * Two time limits: a short one for user searches, which fall back to keyword search when it is exceeded, and a longer one for index writes,
 * which the outbox retries. SDK retries are disabled for both. Every response is validated before use: one finite, non-zero vector of one
 * dimension per input, in input order. Anything else is an {@link EmbeddingFailedException}, so a write never stores a partial or invalid result.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityEmbeddingClient {

    private final WeaviateConfigurationProperties properties;

    private final WeaviateEmbeddingProperties embeddingProperties;

    private final LlmClientService llmClientService;

    @Nullable
    private final String presetName;

    public SearchableEntityEmbeddingClient(WeaviateConfigurationProperties properties, WeaviateEmbeddingProperties embeddingProperties, LlmClientService llmClientService) {
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
        this.llmClientService = llmClientService;
        this.presetName = presetName(properties);
    }

    /**
     * Resolves the embedding model of hybrid search.
     *
     * @param properties    the Weaviate configuration
     * @param llmProperties the LLM catalog
     * @return the configured model of the {@code embedding-llm-preset}, or null when none is assigned
     */
    public static @Nullable String embeddingModel(WeaviateConfigurationProperties properties, LlmProperties llmProperties) {
        String presetName = presetName(properties);
        LlmProperties.Preset preset = presetName == null ? null : llmProperties.preset(presetName);
        return preset == null ? null : preset.model();
    }

    private static @Nullable String presetName(WeaviateConfigurationProperties properties) {
        String presetName = properties.embeddingLlmPreset();
        return presetName == null || presetName.isBlank() ? null : presetName.strip();
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
        return embed(List.of(input), embeddingProperties.queryTimeout()).getFirst();
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
        return embed(texts, embeddingProperties.writeTimeout());
    }

    private List<float[]> embed(List<String> texts, Duration timeout) {
        if (presetName == null) {
            throw new EmbeddingFailedException("No embedding-llm-preset is assigned");
        }
        EmbeddingResponseDTO response;
        try {
            response = toResponse(llmClientService.embed(presetName, texts, timeout));
        }
        catch (OpenAIException e) {
            throw new EmbeddingFailedException("Embedding request failed: " + e.getMessage(), e);
        }
        return validate(response, texts.size());
    }

    /**
     * Copies the vectors and their positions out of the provider's response; a missing field fails here, before the validation.
     */
    private static EmbeddingResponseDTO toResponse(CreateEmbeddingResponse response) {
        List<EmbeddingResponseDTO.EmbeddingDataDTO> data = new ArrayList<>(response.data().size());
        for (Embedding embedding : response.data()) {
            if (embedding == null) {
                // the validation rejects the empty entry
                data.add(null);
                continue;
            }
            List<Float> values = embedding.embedding();
            float[] vector = new float[values.size()];
            for (int i = 0; i < vector.length; i++) {
                Float value = values.get(i);
                if (value == null) {
                    throw new EmbeddingFailedException("Embedding contains an empty value");
                }
                vector[i] = value;
            }
            // an index beyond int becomes -1, which the validation rejects like any other index outside the inputs
            long index = embedding.index();
            data.add(new EmbeddingResponseDTO.EmbeddingDataDTO(index < 0 || index > Integer.MAX_VALUE ? -1 : (int) index, vector));
        }
        return new EmbeddingResponseDTO(data);
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
