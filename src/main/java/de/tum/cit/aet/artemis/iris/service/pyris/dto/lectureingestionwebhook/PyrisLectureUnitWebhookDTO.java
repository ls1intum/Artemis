package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.VideoSourceType;

/**
 * Represents a webhook data transfer object for lecture units in the Pyris system.
 * This DTO is used to encapsulate the information related to updates of lecture units,
 * providing necessary details such as lecture and course identifiers, names, and descriptions.
 * <p>
 * The {@code videoSourceType} field tells Pyris how to obtain the audio track for transcription
 * (e.g. download an HLS playlist for TUM Live, or use {@code yt-dlp} for YouTube). It is
 * {@code null} when the unit has no video or the source could not be identified.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)

public record PyrisLectureUnitWebhookDTO(String pdfFile, int attachmentVersion, PyrisLectureTranscriptionDTO transcription, long lectureUnitId, String lectureUnitName,
        long lectureId, String lectureName, long courseId, String courseName, String courseDescription, String lectureUnitLink, String videoLink, VideoSourceType videoSourceType) {
}
