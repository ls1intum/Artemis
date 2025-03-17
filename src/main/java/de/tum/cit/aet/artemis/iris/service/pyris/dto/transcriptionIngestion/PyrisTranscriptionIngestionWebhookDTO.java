package de.tum.cit.aet.artemis.iris.service.pyris.dto.transcriptionIngestion;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;

/**
 * Represents a webhook data transfer object for lecture transcriptions on lecture unit level in the Pyris system.
 * This DTO is used to encapsulate the information related to updates of lecture transcriptions,
 * providing necessary details such as lecture and course identifiers, names, and descriptions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)

public record PyrisTranscriptionIngestionWebhookDTO(LectureTranscription transcription, long lectureId, String lectureName, long courseId, String courseName,
        String courseDescription, long lectureUnitId, String lectureUnitName, String lectureUnitLink) {
}
