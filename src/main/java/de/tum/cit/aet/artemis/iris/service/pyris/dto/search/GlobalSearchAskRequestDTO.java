package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body sent by the Angular client to {@code POST api/iris/search-answer}.
 * Contains the user's search query, the maximum number of sources to retrieve, and a
 * client-generated correlation ID that Pyris echoes back in its webhook callbacks so
 * Artemis can route WebSocket messages to the correct subscriber. An optional course id
 * (the search UI's active course filter) scopes retrieval and entity candidates to that course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GlobalSearchAskRequestDTO(@NotBlank String query, @Min(1) @Max(5) int limit, @NotNull UUID runId, @Nullable Long courseId) {

    public GlobalSearchAskRequestDTO(String query, int limit, UUID runId) {
        this(query, limit, runId, null);
    }
}
