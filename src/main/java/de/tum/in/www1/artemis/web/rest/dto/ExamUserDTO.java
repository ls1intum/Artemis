package de.tum.in.www1.artemis.web.rest.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

public record ExamUserDTO(@Size(max = 50) String login, @Size(max = 50) String firstName, @Size(max = 50) String lastName, @Size(max = 10) String registrationNumber,
        @Email @Size(max = 100) String email, String studentIdentifier, String room, String seat, Boolean didCheckImage, Boolean didCheckName, Boolean didCheckRegistrationNumber,
        Boolean didCheckLogin, @Size(max = 100) String signingImagePath) {
}
