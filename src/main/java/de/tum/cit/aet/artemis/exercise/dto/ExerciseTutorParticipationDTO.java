package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionDTO;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

/**
 * The requesting tutor's participation in an exercise, as the assessment dashboard reads it off
 * {@code exercise.tutorParticipations}.
 * <p>
 * Unlike {@link de.tum.cit.aet.artemis.assessment.dto.TutorParticipationDTO}, which reports a stored participation, the
 * dashboard also reports the synthetic {@code NOT_PARTICIPATED} participation of a tutor who has not started yet. That
 * one has no id, no tutor and no exercise, so those components are absent here rather than mandatory.
 *
 * @param id                        the id of the participation, absent when the tutor has not started participating
 * @param status                    the participation status the dashboard drives its steps from
 * @param trainedExampleSubmissions the example submissions the tutor already worked through
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTutorParticipationDTO(@Nullable Long id, @Nullable TutorParticipationStatus status, @Nullable List<ExampleSubmissionDTO> trainedExampleSubmissions) {

    /**
     * Maps a tutor participation, tolerating the synthetic one the dashboard reports for a tutor who has not started.
     *
     * @param tutorParticipation the tutor participation
     * @return the tutor participation as the assessment dashboard reads it
     */
    public static ExerciseTutorParticipationDTO of(TutorParticipation tutorParticipation) {
        var trained = tutorParticipation.getTrainedExampleSubmissions();
        List<ExampleSubmissionDTO> trainedDTOs = trained == null || !Hibernate.isInitialized(trained) ? null
                : trained.stream().filter(Objects::nonNull).map(ExampleSubmissionDTO::of).toList();
        return new ExerciseTutorParticipationDTO(tutorParticipation.getId(), tutorParticipation.getStatus(), trainedDTOs);
    }
}
