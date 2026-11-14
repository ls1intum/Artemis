package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchResultDTO(CourseDTO course, LectureDTO lecture, LectureUnitDTO lectureUnit, String snippet) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(name = "LectureSearchCourseDTO")
    public record CourseDTO(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(name = "LectureSearchLectureDTO")
    public record LectureDTO(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(name = "LectureSearchLectureUnitDTO")
    public record LectureUnitDTO(long id, String name, String link, int pageNumber, String sourceType, Map<String, Object> queryParams, @Nullable String displayMeta) {
    }
}
