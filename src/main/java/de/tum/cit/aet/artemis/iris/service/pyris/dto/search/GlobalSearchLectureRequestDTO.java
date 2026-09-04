package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body sent by the Angular client to {@code POST api/iris/lecture-search}: the user's query, the result limit, and an optional course filter.
 * <p>
 * Deliberately does NOT carry an {@code accessContext}: access is resolved server-side from the authenticated user and must never be client-controlled. The Pyris-bound
 * {@link PyrisLectureSearchRequestDTO} (which does carry the access context) is built from this in the connector.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GlobalSearchLectureRequestDTO(@NotBlank String query, @Min(1) @Max(20) int limit, @Nullable List<Long> courseIds) {
}
