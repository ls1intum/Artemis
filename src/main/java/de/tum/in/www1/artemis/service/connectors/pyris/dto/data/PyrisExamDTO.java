package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;

public record PyrisExamDTO(long id, String title, boolean isTextExam, Instant startDate, Instant endDate, Instant publishResultsDate, Instant examStudentReviewStart,
        Instant examStudentReviewEnd

) {
}
