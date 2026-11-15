package de.tum.cit.aet.artemis.globalsearch.dto.embedding;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body of the OpenAI-compatible embeddings endpoint.
 *
 * @param model the embedding model
 * @param input the texts to embed, in order
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EmbeddingRequestDTO(String model, List<String> input) {
}
