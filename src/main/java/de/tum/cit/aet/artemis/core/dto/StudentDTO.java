package de.tum.cit.aet.artemis.core.dto;

import java.util.Objects;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentDTO(@Size(max = 50) String login, @Size(max = 50) String firstName, @Size(max = 50) String lastName, @Size(max = 10) String registrationNumber,
        @Email @Size(max = 100) String email) {

    public StudentDTO(User user) {
        this(user.getLogin(), user.getFirstName(), user.getLastName(), user.getRegistrationNumber(), user.getEmail());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        StudentDTO that = (StudentDTO) obj;
        return Objects.equals(registrationNumber, that.registrationNumber) || Objects.equals(login, that.login) || Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registrationNumber) ^ Objects.hash(login) ^ Objects.hash(email);
    }
}
