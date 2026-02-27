package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

// TODO: Refactor to nested structure once Pyris is updated to return nested JSON.
// Planned shape: { course: { id, name }, lecture: { id, name }, lectureUnit: { id, name, link, pageNumber }, snippet, baseUrl }
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchResultDTO(@JsonAlias("lecture_unit_id") long lectureUnitId, @JsonAlias("lecture_unit_name") String lectureUnitName,
        @JsonAlias("lecture_unit_link") String lectureUnitLink, @JsonAlias("lecture_id") long lectureId, @JsonAlias("lecture_name") String lectureName,
        @JsonAlias("course_id") long courseId, @JsonAlias("course_name") String courseName, @JsonAlias("page_number") int pageNumber, @JsonAlias("base_url") String baseUrl,
        String snippet) {
}
