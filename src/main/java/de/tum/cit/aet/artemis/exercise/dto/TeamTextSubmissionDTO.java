package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * A team's text submission as it is broadcast to the other members of the team while they collaborate on it.
 * <p>
 * Results are deliberately absent: the save clears them before this payload is built, so the entity payload never
 * carried any either.
 *
 * @param id                     the id of the submission
 * @param submissionExerciseType the concrete submission kind; the client discriminates on it
 * @param text                   the text the team is working on
 * @param language               the detected language of that text
 * @param submitted              whether the submission was submitted
 * @param submissionDate         when it was last saved
 * @param type                   how the submission came about
 * @param participation          the participation the submission belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamTextSubmissionDTO(Long id, String submissionExerciseType, String text, Language language, Boolean submitted, ZonedDateTime submissionDate, SubmissionType type,
        StudentParticipationDTO participation) implements Serializable {

    /**
     * Projects a saved text submission for the team sync topic.
     *
     * @param submission the submission to project
     * @param text       the text of the submission
     * @param language   the language of the submission
     * @return the projected submission
     */
    public static TeamTextSubmissionDTO of(Submission submission, String text, Language language) {
        StudentParticipationDTO participation = submission.getParticipation() instanceof StudentParticipation studentParticipation
                ? StudentParticipationDTO.of(studentParticipation, true)
                : null;
        return new TeamTextSubmissionDTO(submission.getId(), submission.getSubmissionExerciseType(), text, language, submission.isSubmitted(), submission.getSubmissionDate(),
                submission.getType(), participation);
    }
}
