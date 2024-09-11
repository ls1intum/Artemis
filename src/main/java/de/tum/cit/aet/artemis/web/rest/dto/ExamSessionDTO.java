package de.tum.cit.aet.artemis.web.rest.dto;

import java.time.Instant;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.SuspiciousSessionReason;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamSessionDTO(long id, String browserFingerprintHash, String ipAddress, Set<SuspiciousSessionReason> suspiciousReasons, Instant createdDate,
        StudentExamWithIdAndExamAndUserDTO studentExam) {
}
