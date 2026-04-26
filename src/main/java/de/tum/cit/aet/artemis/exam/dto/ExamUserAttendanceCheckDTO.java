package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamUserAttendanceCheckDTO(Long id, String login, String firstName, String lastName, String registrationNumber, String email, Boolean didCheckImage,
        Boolean didCheckName, Boolean didCheckRegistrationNumber, Boolean didCheckLogin, String signingImagePath, String studentImagePath, Boolean started, Boolean submitted) {
}
