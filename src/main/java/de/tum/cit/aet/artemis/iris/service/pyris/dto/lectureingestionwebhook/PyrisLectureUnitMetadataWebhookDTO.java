package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitMetadataWebhookDTO(Long lectureUnitId, String lectureUnitName, String lectureUnitLink, Long lectureId, String lectureName, Long courseId,
        String courseName, String courseDescription, String videoLink, String baseUrl) {

    /**
     * A TUM Live video link is a signed playlist that works as a credential for seven hours, so it is left out of log output.
     */
    @Override
    public String toString() {
        return "PyrisLectureUnitMetadataWebhookDTO[lectureUnitId=" + lectureUnitId + ", lectureUnitName=" + lectureUnitName + ", lectureUnitLink=" + lectureUnitLink
                + ", lectureId=" + lectureId + ", lectureName=" + lectureName + ", courseId=" + courseId + ", courseName=" + courseName + ", courseDescription=" + courseDescription
                + ", videoLink=" + (videoLink == null ? null : "[REDACTED]") + ", baseUrl=" + baseUrl + "]";
    }
}
