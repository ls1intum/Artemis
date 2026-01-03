package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorParticipationDTO(long id, long exerciseId, long tutorId, @NotNull TutorParticipationStatus status,
        @NotNull Set<ExampleParticipationDTO> trainedExampleParticipations) {

    /**
     * Convert a TutorParticipation entity to a TutorParticipationDTO.
     *
     * @param tutorParticipation the TutorParticipation to convert
     */
    public static TutorParticipationDTO of(TutorParticipation tutorParticipation) {
        Set<ExampleParticipationDTO> trained = tutorParticipation.getTrainedExampleParticipations() == null ? Set.of()
                : tutorParticipation.getTrainedExampleParticipations().stream().filter(Objects::nonNull).map(ExampleParticipationDTO::of).collect(Collectors.toSet());
        return new TutorParticipationDTO(tutorParticipation.getId(), tutorParticipation.getAssessedExercise().getId(), tutorParticipation.getTutor().getId(),
                tutorParticipation.getStatus(), trained);
    }
}
