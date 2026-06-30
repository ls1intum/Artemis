package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for seeding an Iris chat session with a Q&A pair carried over from global search.
 *
 * @param query  the original search query the student typed
 * @param answer the Iris answer that was shown in the global search result card
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IrisGlobalSearchSeedDTO(String query, String answer) {
}
