package de.tum.cit.aet.artemis.service.connectors.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a webhook data transfer object for lecture units in the Pyris system.
 * This DTO is used to encapsulate the information related to updates of lecture units,
 * providing necessary details such as lecture and course identifiers, names, and descriptions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitWebhookDTO(Boolean toUpdate, String artemisBaseUrl, String pdfFile, Long lectureUnitId, String lectureUnitName, Long lectureId, String lectureName,
        Long courseId, String courseName, String courseDescription) {
}
