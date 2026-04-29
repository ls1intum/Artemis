package de.tum.cit.aet.artemis.core.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO used for the admin CSV user import. In addition to the regular {@link StudentDTO} fields, it carries an optional
 * password that admins can set when creating internal users in bulk (e.g. test personas).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserImportDTO(@Size(max = 50) String login, @Size(max = 50) String firstName, @Size(max = 50) String lastName, @Size(max = 10) String registrationNumber,
        @Email @Size(max = 100) String email, @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH) String password) {
}
