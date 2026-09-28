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
 */
public record PyrisLectureSearchRequestDTO(@NotBlank String query, @Min(1) @Max(20) int limit, @Nullable List<Long> courseIds, @Nullable List<Long> excludeCourseIds,
        @Nullable PyrisAccessContextDTO accessContext) {
}
