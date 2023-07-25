package de.tum.in.www1.artemis.web.rest.dto;

import java.time.Instant;
import java.util.Set;

import de.tum.in.www1.artemis.domain.exam.SuspiciousSessionReason;

public record ExamSessionDTO(long id, String sessionToken, String browserFingerprintHash, String userAgent, String instanceId, String ipAddress,
        Set<SuspiciousSessionReason> suspiciousReasons, Instant createdDate, StudentExamWithIdAndUserDTO studentExam) {
}
