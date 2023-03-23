package de.tum.in.www1.artemis.web.rest.dto;

public record ExamUserAttendanceCheckDTO(Long id, String studentImagePath, String login, String registrationNumber, String signingImagePath, Boolean started, Boolean submitted) {
}
