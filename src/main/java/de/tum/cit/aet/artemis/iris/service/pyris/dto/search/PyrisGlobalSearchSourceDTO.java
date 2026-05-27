package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Unified source result returned by the Pyris global search pipeline.
 * Covers all entity types: lecture slides, exercises, FAQs, exams, channels.
 * The {@code sourceType} discriminator drives the UI icon selection.
 * The lecture search endpoint still uses {@link PyrisLectureSearchResultDTO} unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PyrisGlobalSearchSourceDTO(@JsonProperty("sourceType") String sourceType, @JsonProperty("entityId") long entityId,
        @JsonProperty("course") PyrisLectureSearchResultDTO.CourseDTO course, @JsonProperty("title") String title, @JsonProperty("snippet") String snippet,
        @JsonProperty("exerciseType") String exerciseType, @JsonProperty("lecture") PyrisLectureSearchResultDTO.LectureDTO lecture,
        @JsonProperty("lectureUnit") PyrisLectureSearchResultDTO.LectureUnitDTO lectureUnit) {
}
