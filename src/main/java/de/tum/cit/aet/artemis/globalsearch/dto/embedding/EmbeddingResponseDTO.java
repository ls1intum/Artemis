package de.tum.cit.aet.artemis.globalsearch.dto.embedding;

import java.util.Arrays;
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

        @Override
        public boolean equals(Object other) {
            return other instanceof EmbeddingDataDTO(int otherIndex, float[] otherEmbedding) && index == otherIndex && Arrays.equals(embedding, otherEmbedding);
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(index) + Arrays.hashCode(embedding);
        }

        /**
         * Reports the number of dimensions instead of the vector itself, which is too large to print.
         */
        @Override
        public String toString() {
            return "EmbeddingDataDTO[index=" + index + ", dimensions=" + (embedding == null ? 0 : embedding.length) + "]";
        }
    }
}
