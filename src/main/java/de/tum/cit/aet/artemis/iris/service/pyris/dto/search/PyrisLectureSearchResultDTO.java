package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;

// TODO: Refactor to nested structure once Pyris is updated to return nested JSON.
// Planned shape: { course: { id, name }, lecture: { id, name }, lectureUnit: { id, name, link, pageNumber }, snippet }
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchResultDTO(long lectureUnitId, String lectureUnitName, String lectureUnitLink, long lectureId, String lectureName, long courseId, String courseName,
        int pageNumber, String snippet) {
}
