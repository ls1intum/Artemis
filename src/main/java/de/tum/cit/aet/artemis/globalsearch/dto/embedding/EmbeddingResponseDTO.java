package de.tum.cit.aet.artemis.globalsearch.dto.embedding;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body of the OpenAI-compatible embeddings endpoint; only the vectors and their positions are read.
 *
 * @param data one entry per input text
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbeddingResponseDTO(List<EmbeddingDataDTO> data) {

    /**
     * One embedding of the response.
     *
     * @param index     position of the input text the vector belongs to
     * @param embedding the vector
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingDataDTO(int index, float[] embedding) {
    }
}
