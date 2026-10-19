package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitMetadataWebhookDTO(Long lectureUnitId, String lectureUnitName, String lectureUnitLink, Long lectureId, String lectureName, Long courseId,
        String courseName, String courseDescription, String videoLink, String baseUrl) {
}
