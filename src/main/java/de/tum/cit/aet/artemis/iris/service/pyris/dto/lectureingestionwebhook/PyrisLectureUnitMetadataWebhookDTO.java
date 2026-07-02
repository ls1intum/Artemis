package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

public record PyrisLectureUnitMetadataWebhookDTO(Long lectureUnitId, String lectureUnitName, String lectureUnitLink, Long lectureId, String lectureName, Long courseId,
        String courseName, String courseDescription, String videoLink, String baseUrl) {
}
