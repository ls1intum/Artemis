package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook;

public record PyrisLectureUnitWebhookDTO(Boolean toUpdate, String pdfFile, int lectureUnitId, String lectureUnitName, int lectureId, String lectureName, int courseId,
        String courseName, String courseDescription) {
}
