package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entity source used by the Pyris global-search answer ({@code entitySources} on the status
 * update): course information (an exercise, exam, channel, FAQ, lecture or course entry) the answer
 * drew on, alongside the lecture-content {@code sources}. The {@code snippet} carries the rendered
 * entity card.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisEntitySourceDTO(String entityType, @Nullable Long entityId, PyrisLectureSearchResultDTO.@Nullable CourseDTO course, @Nullable String title,
        @Nullable String snippet, @Nullable String link) {
}
