package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitWebhookDTO(Boolean toUpdate, String artemisBaseUrl, String pdfFile, int lectureUnitId, String lectureUnitName, int lectureId, String lectureName,
        int courseId, String courseName, String courseDescription) {
}
