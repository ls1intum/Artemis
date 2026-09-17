package de.tum.cit.aet.artemis.exam.dto.conduction;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * Polymorphic projection of a {@link StudentParticipation} in the conduction payload. The common fields live in
 * {@link ParticipationBaseForConductionDTO}; the programming-only repository coordinates are unwrapped so the wire stays
 * flat and byte-compatible with the entity payload the (unchanged) client model deserializes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentParticipationForConductionDTO(@JsonUnwrapped ParticipationBaseForConductionDTO base,
        @Nullable @JsonUnwrapped ProgrammingParticipationFieldsForConductionDTO programmingParticipation) {

    /**
     * Converts a StudentParticipation into a StudentParticipationForConductionDTO, adding the programming repository
     * coordinates for programming participations.
     *
     * @param participation the participation to convert
     * @return the converted DTO, or null if the participation is null
     */
    public static StudentParticipationForConductionDTO of(StudentParticipation participation) {
        if (participation == null) {
            return null;
        }
        ProgrammingParticipationFieldsForConductionDTO programmingParticipation = null;
        if (participation instanceof ProgrammingExerciseStudentParticipation programming) {
            programmingParticipation = ProgrammingParticipationFieldsForConductionDTO.of(programming);
        }
        return new StudentParticipationForConductionDTO(ParticipationBaseForConductionDTO.of(participation), programmingParticipation);
    }
}
