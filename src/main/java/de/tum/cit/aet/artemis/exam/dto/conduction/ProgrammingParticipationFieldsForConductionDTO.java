package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * Programming-participation-specific fields carried in the conduction payload (unwrapped into the participation
 * object). These are the repository coordinates the code editor needs to check out and work on the student's exam
 * repository during conduction.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingParticipationFieldsForConductionDTO(String repositoryUri, String userIndependentRepositoryUri, String branch, String buildPlanId) {

    /**
     * Extracts the programming-specific participation fields.
     *
     * @param participation the programming participation to convert
     * @return the programming-specific fields
     */
    public static ProgrammingParticipationFieldsForConductionDTO of(ProgrammingExerciseStudentParticipation participation) {
        return new ProgrammingParticipationFieldsForConductionDTO(participation.getRepositoryUri(), participation.getUserIndependentRepositoryUri(), participation.getBranch(),
                participation.getBuildPlanId());
    }
}
