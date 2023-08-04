package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SuspiciousExamSessionsDTO(Set<ExamSessionDTO> examSessions) {
}
