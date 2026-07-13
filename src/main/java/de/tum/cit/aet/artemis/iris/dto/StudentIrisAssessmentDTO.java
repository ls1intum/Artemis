package de.tum.cit.aet.artemis.iris.dto;

import java.io.Serializable;
import java.util.Optional;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentIrisAssessmentDTO(String login, String name) implements Serializable {

    @Nullable
    public static StudentIrisAssessmentDTO of(User student) {
        return Optional.ofNullable(student).map(value -> new StudentIrisAssessmentDTO(value.getLogin(), value.getName())).orElse(null);
    }
}
