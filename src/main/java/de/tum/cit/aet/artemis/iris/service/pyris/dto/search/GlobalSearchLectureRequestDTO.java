package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body sent by the Angular client to {@code POST api/iris/lecture-search}: the user's query, the result limit, and the optional course filters.
 * <p>
 * {@code courseIds} narrows the search to those courses; {@code excludeCourseIds} hides them. The two mirror the course chips of the search palette, where a chip is
 * either an inclusion or an exclusion.
 * <p>
 * Deliberately does NOT carry an {@code accessContext}: access is resolved server-side from the authenticated user and must never be client-controlled. The Pyris-bound
 * {@link PyrisLectureSearchRequestDTO} (which does carry the access context) is built from this in the connector.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GlobalSearchLectureRequestDTO(@NotBlank String query, @Min(1) @Max(20) int limit, @Nullable @Size(max = MAX_COURSE_ID_FILTERS) List<Long> courseIds,
        @Nullable @Size(max = MAX_COURSE_ID_FILTERS) List<Long> excludeCourseIds) {

    /**
     * Upper bound on the number of course ids honoured per list, mirroring the metadata search endpoint. A caller has
     * no reason to name more courses than this, and an unrestricted caller's exclusions travel on to Pyris, where an
     * unbounded list would become an unbounded query filter.
     */
    public static final int MAX_COURSE_ID_FILTERS = 100;
}
