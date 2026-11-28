package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorParticipationDTO(long id, long exerciseId, long tutorId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TutorParticipationStatus status,
        @Nullable Set<ExampleSubmissionDTO> trainedExampleSubmissions) {

    /**
     * Convert a TutorParticipation entity to a TutorParticipationDTO.
     *
     * @param tutorParticipation the TutorParticipation to convert
     */
    public static TutorParticipationDTO of(TutorParticipation tutorParticipation) {
        Set<ExampleSubmissionDTO> trained = tutorParticipation.getTrainedExampleSubmissions() == null ? Set.of()
                : tutorParticipation.getTrainedExampleSubmissions().stream().filter(Objects::nonNull).map(ExampleSubmissionDTO::of).collect(Collectors.toSet());
        return new TutorParticipationDTO(tutorParticipation.getId(), tutorParticipation.getAssessedExercise().getId(), tutorParticipation.getTutor().getId(),
                tutorParticipation.getStatus(), trained);
    }
}
