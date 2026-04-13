package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchResultDTO(CourseDTO course, LectureDTO lecture, @JsonAlias("lecture_unit") LectureUnitDTO lectureUnit, String snippet) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDTO(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureDTO(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureUnitDTO(long id, String name, String link, @JsonAlias("page_number") int pageNumber,
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonAlias("start_time") Integer startTime) {
    }
}
