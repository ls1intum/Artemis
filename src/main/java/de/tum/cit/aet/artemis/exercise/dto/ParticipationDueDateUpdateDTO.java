package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * DTO for updating a participation's individual due date.
 *
 * @param id                the ID of the participation to update
 * @param exerciseId        the ID of the exercise the participation belongs to
 * @param individualDueDate the new individual due date (can be null to remove)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationDueDateUpdateDTO(@NotNull Long id, @NotNull Long exerciseId, ZonedDateTime individualDueDate) {

    /**
     * Creates a ParticipationDueDateUpdateDTO from a StudentParticipation.
     *
     * @param participation the participation to convert
     * @return the DTO representation
     */
    public static ParticipationDueDateUpdateDTO of(StudentParticipation participation) {
        Long exerciseId = participation.getExercise() != null ? participation.getExercise().getId() : null;
        return new ParticipationDueDateUpdateDTO(participation.getId(), exerciseId, participation.getIndividualDueDate());
    }
}
