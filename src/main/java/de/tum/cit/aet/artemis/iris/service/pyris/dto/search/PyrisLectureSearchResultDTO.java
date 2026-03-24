package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchResultDTO(CourseDTO course, LectureDTO lecture, LectureUnitDTO lectureUnit, String snippet) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDTO(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureDTO(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureUnitDTO(long id, String name, String link, int pageNumber) {
    }
}
