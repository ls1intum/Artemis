package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamUserAttendanceCheckDTO(Long id, String studentImagePath, String login, String registrationNumber, String signingImagePath, Boolean started, Boolean submitted) {
}
