package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchResultDTO(Course course, Lecture lecture, LectureUnit lectureUnit, String snippet) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Course(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Lecture(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureUnit(long id, String name, String link, int pageNumber) {
    }
}
