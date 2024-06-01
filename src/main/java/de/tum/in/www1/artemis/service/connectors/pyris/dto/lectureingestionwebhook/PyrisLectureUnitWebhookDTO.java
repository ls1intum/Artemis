package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PyrisLectureUnitWebhookDTO(Boolean toUpdate, @JsonProperty("baseUrl") String artemisBaseUrl, String pdfFile, int lectureUnitId, String lectureUnitName, int lectureId,
        String lectureName, int courseId, String courseName, String courseDescription) {
}
