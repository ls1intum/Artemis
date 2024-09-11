package de.tum.cit.aet.artemis.exam.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.artemis.exam.dto.ExamSessionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SuspiciousExamSessionsDTO(Set<ExamSessionDTO> examSessions) {
}
