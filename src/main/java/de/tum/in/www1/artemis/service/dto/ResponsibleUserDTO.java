package de.tum.in.www1.artemis.service.dto;

import java.util.Objects;

/**
 * A DTO representing a course's responsible contact, i.e., a person to report misconduct to.
 */
public record ResponsibleUserDTO(String firstName, String lastName, String email) {

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ResponsibleUserDTO that = (ResponsibleUserDTO) o;
        return Objects.equals(firstName, that.firstName) && Objects.equals(lastName, that.lastName) && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, email);
    }

    @Override
    public String toString() {
        return "ResponsibleUserDTO{" + "firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", email='" + email + '\'' + '}';
    }
}
