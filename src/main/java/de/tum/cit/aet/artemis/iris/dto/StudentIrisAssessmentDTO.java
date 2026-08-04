package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;
import java.util.Optional;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * DTO for the student a given Iris assessment belongs to.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentIrisAssessmentDTO(String login, String name) implements Serializable {

    /**
     * Creates a DTO for the given student.
     *
     * @param student the student to convert
     * @return the DTO or null if the student is null
     */
    @Nullable
    public static StudentIrisAssessmentDTO of(User student) {
        return Optional.ofNullable(student).map(value -> new StudentIrisAssessmentDTO(value.getLogin(), value.getName())).orElse(null);
    }
}
