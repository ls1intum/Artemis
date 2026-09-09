package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

/**
 * Represents a webhook data transfer object for lecture units in the Pyris system.
 * This DTO is used to encapsulate the information related to updates of lecture units,
 * providing necessary details such as lecture and course identifiers, names, and descriptions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitWebhookDTO(String pdfFile, int attachmentVersion, PyrisLectureTranscriptionDTO transcription, long lectureUnitId, String lectureUnitName,
        long lectureId, String lectureName, long courseId, String courseName, String courseDescription, String lectureUnitLink, String videoLink, VideoSourceType videoSourceType,
        String contentFingerprint, boolean forceReingest) {

    /**
     * Compatibility constructor for callers that do not request a forced re-ingestion
     * (deletions and regular ingestion dispatches).
     *
     * @param pdfFile            base64 encoded PDF
     * @param attachmentVersion  version of the attachment
     * @param transcription      existing transcription, if any
     * @param lectureUnitId      id of the lecture unit
     * @param lectureUnitName    name of the lecture unit
     * @param lectureId          id of the lecture
     * @param lectureName        name of the lecture
     * @param courseId           id of the course
     * @param courseName         name of the course
     * @param courseDescription  description of the course
     * @param lectureUnitLink    link to the lecture unit
     * @param videoLink          link to the video
     * @param videoSourceType    type of the video source
     * @param contentFingerprint fingerprint of the unit's source content
     */
    public PyrisLectureUnitWebhookDTO(String pdfFile, int attachmentVersion, PyrisLectureTranscriptionDTO transcription, long lectureUnitId, String lectureUnitName, long lectureId,
            String lectureName, long courseId, String courseName, String courseDescription, String lectureUnitLink, String videoLink, VideoSourceType videoSourceType,
            String contentFingerprint) {
        this(pdfFile, attachmentVersion, transcription, lectureUnitId, lectureUnitName, lectureId, lectureName, courseId, courseName, courseDescription, lectureUnitLink, videoLink,
                videoSourceType, contentFingerprint, false);
    }
}
