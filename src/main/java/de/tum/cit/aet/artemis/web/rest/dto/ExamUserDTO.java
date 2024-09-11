package de.tum.cit.aet.artemis.web.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contains the information about an exam user
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamUserDTO(@Size(max = 50) String login, @Size(max = 50) String firstName, @Size(max = 50) String lastName, @Size(max = 10) String registrationNumber,
        @Email @Size(max = 100) String email, String studentIdentifier, String room, String seat, boolean didCheckImage, boolean didCheckName, boolean didCheckRegistrationNumber,
        boolean didCheckLogin, @Size(max = 100) String signingImagePath) {
}
