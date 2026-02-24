package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchResultDTO(long lectureUnitId, String lectureUnitName, String lectureUnitLink, long lectureId, String lectureName, long courseId, String courseName,
        int pageNumber, String snippet, String baseUrl) {
}
