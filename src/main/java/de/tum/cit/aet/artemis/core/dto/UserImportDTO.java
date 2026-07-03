package de.tum.cit.aet.artemis.core.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO used for the admin CSV user import. In addition to the regular {@link StudentDTO} fields, it carries an optional
 * password that admins can set when creating internal users in bulk (e.g. test personas).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserImportDTO(@Nullable @Size(max = 50) String login, @Nullable @Size(max = 50) String firstName, @Nullable @Size(max = 50) String lastName,
        @Nullable @Size(max = 10) String registrationNumber, @Nullable @Email @Size(max = 100) String email,
        @Nullable @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH) String password) {

    public StudentDTO toStudentDTO() {
        return new StudentDTO(login, firstName, lastName, registrationNumber, email);
    }
}
