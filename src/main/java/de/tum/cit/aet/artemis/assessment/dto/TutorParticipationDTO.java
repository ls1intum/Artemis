package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorParticipationDTO(long id, long exerciseId, long tutorId, String status, int trainedCount) {

    /**
     * Convert a TutorParticipation entity to a TutorParticipationDTO.
     *
     * @param tutorParticipation the TutorParticipation to convert
     */
    public TutorParticipationDTO(TutorParticipation tutorParticipation) {
        this(tutorParticipation.getId(), tutorParticipation.getAssessedExercise().getId(), tutorParticipation.getTutor().getId(), tutorParticipation.getStatus().name(),
                tutorParticipation.getTrainedExampleSubmissions().size());
    }
}
