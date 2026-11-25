package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * The scalar data of a submission in the team assessment queue, selected without the submitted answer.
 *
 * @param id                  the submission id
 * @param submitted           whether the team submitted
 * @param type                how the submission was triggered
 * @param exampleSubmission   whether this is an example submission
 * @param submissionDate      when the submission was made
 * @param participationId     the participation the submission belongs to
 * @param initializationState the state the participation is in
 * @param initializationDate  when the participation started
 * @param individualDueDate   the individual due date of the participation, if one was set
 * @param participantName     the team name
 * @param buildFailed         whether the build failed, for programming submissions
 * @param commitHash          the commit the submission was built from, for programming submissions
 * @param language            the language of the text, for text submissions
 */
public record AssessmentQueueSubmissionDTO(Long id, Boolean submitted, @Nullable SubmissionType type, Boolean exampleSubmission, @Nullable ZonedDateTime submissionDate,
        Long participationId, @Nullable InitializationState initializationState, @Nullable ZonedDateTime initializationDate, @Nullable ZonedDateTime individualDueDate,
        @Nullable String participantName, @Nullable Boolean buildFailed, @Nullable String commitHash, @Nullable Language language) {

    /**
     * Builds the submission as the submission list endpoints report it.
     *
     * @param exerciseType the type of the exercise, which is also the type of every submission of it
     * @param results      the results of the submission
     * @return the submission response
     */
    public SubmissionResponseDTO toResponse(ExerciseType exerciseType, List<AssessmentQueueResultDTO> results) {
        String participationType = exerciseType == ExerciseType.PROGRAMMING ? "programming" : StudentParticipation.TYPE;
        var participation = new SubmissionResponseDTO.SubmissionParticipationDTO(participationId, participationType, false, initializationState, initializationDate,
                individualDueDate, participantName, null);
        return new SubmissionResponseDTO(id, submitted, type, exampleSubmission, submissionDate, exerciseType.getValue(), participation,
                results.stream().map(AssessmentQueueResultDTO::toResponse).toList(), buildFailed, commitHash, null, language, null);
    }
}
