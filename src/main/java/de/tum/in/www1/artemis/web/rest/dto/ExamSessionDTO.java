package de.tum.in.www1.artemis.web.rest.dto;

import java.time.Instant;
import java.util.Set;

import de.tum.in.www1.artemis.domain.exam.SuspiciousSessionReason;

public record ExamSessionDTO(long id, String browserFingerprintHash, String ipAddress, Set<SuspiciousSessionReason> suspiciousReasons, Instant createdDate,
        StudentExamWithIdAndExamAndUserDTO studentExam) {
}
