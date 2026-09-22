package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
/**
 * Request sent to Pyris {@code POST /api/v1/search/lectures}.
 * <p>
 * {@code excludeCourseIds} only has to travel when Artemis cannot subtract the exclusion itself, which is the unrestricted caller: it is sent without a course
 * ceiling, so the query is the only place left to apply the exclusion. Older Pyris versions ignore the field, and every other caller arrives with the exclusion
 * already removed from {@code courseIds}.
 * <p>
 * {@code baseUrl} is this installation's own address, and scopes the search to the rows this installation wrote. Several Artemis installations can share one
 * Weaviate cluster, where course ids collide freely between them, so a course-id filter alone also matches another installation's lecture content - and an
 * unrestricted caller, which sends no course ids at all, matches every row in the cluster. The answer pipeline already carries the same value in its pipeline
 * execution settings; this endpoint has none, so it travels here.
 */
public record PyrisLectureSearchRequestDTO(@NotBlank String query, @Min(1) @Max(20) int limit, @Nullable List<Long> courseIds, @Nullable List<Long> excludeCourseIds,
        @Nullable PyrisAccessContextDTO accessContext, @Nullable String baseUrl) {
}
