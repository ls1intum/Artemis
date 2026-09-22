package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body sent by the Angular client to {@code POST api/iris/search-answer}.
 * Contains the user's search query, the maximum number of sources to retrieve, and a
 * client-generated correlation ID that Pyris echoes back in its webhook callbacks so
 * Artemis can route WebSocket messages to the correct subscriber. The optional course
 * include/exclude lists (the search UI's active course filter) scope retrieval and entity
 * candidates the same way they scope the palette's own visible results.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GlobalSearchAskRequestDTO(@NotBlank String query, @Min(1) @Max(5) int limit, @NotNull UUID runId,
        @Nullable @Size(max = GlobalSearchLectureRequestDTO.MAX_COURSE_ID_FILTERS) List<Long> courseIds,
        @Nullable @Size(max = GlobalSearchLectureRequestDTO.MAX_COURSE_ID_FILTERS) List<Long> excludeCourseIds) {

    public GlobalSearchAskRequestDTO(String query, int limit, UUID runId) {
        this(query, limit, runId, null, null);
    }
}
