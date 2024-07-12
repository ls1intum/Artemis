package de.tum.in.www1.artemis.web.rest.dto;

import java.time.Instant;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.exam.SuspiciousSessionReason;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSessionDTO(long id, String browserFingerprintHash, String ipAddress, Set<SuspiciousSessionReason> suspiciousReasons, Instant createdDate,
        StudentExamWithIdAndExamAndUserDTO studentExam) {
}
