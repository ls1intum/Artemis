package de.tum.in.www1.artemis.service.dto;

import java.util.Objects;

/**
 * A DTO representing a course's responsible user, i.e., a person to report misconduct to.
 */
public record ResponsibleUserDTO(String name, String email) {

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ResponsibleUserDTO that = (ResponsibleUserDTO) o;
        return Objects.equals(name, that.name) && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email);
    }

    @Override
    public String toString() {
        return "ResponsibleUserDTO{" + "name='" + name + '\'' + ", email='" + email + '\'' + '}';
    }
}
