package de.tum.cit.aet.artemis.math.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;

/**
 * Data Transfer Object for {@link MathSubmission}.
 * Used as both request body (student sends {@code submitted}, {@code content}) and response body.
 *
 * @param id             the submission ID (null for new submissions)
 * @param submitted      whether this is a final submission
 * @param submissionDate when the submission was last saved (response only)
 * @param results        automatic grading results (response only)
 * @param participation  the student's participation including exercise info (response only)
 * @param content        the opaque payload carrying the student's work
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MathSubmissionDTO(Long id, Boolean submitted, ZonedDateTime submissionDate, List<MathResultDTO> results, MathParticipationDTO participation, String content) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MathResultDTO(Long id, Double score, AssessmentType assessmentType, ZonedDateTime completionDate) {

        public static MathResultDTO of(Result result) {
            return new MathResultDTO(result.getId(), result.getScore(), result.getAssessmentType(), result.getCompletionDate());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record MathParticipationDTO(Long id, MathExerciseDTO exercise, String studentLogin, String studentName) {

        /**
         * Projects a {@link StudentParticipation} into a participation DTO, including the exercise stub when its
         * math-exercise association is initialized.
         *
         * @param participation the entity to project
         * @return the DTO carrying the user-facing participation fields
         */
        public static MathParticipationDTO of(StudentParticipation participation) {
            MathExerciseDTO exerciseDTO = null;
            if (Hibernate.isInitialized(participation.getExercise()) && participation.getExercise() instanceof MathExercise pe && Hibernate.isInitialized(pe.getCategories())) {
                exerciseDTO = MathExerciseDTO.of(pe);
            }
            String login = participation.getStudent().map(User::getLogin).orElse(null);
            String name = participation.getStudent().map(User::getName).orElse(null);
            return new MathParticipationDTO(participation.getId(), exerciseDTO, login, name);
        }
    }

    /**
     * Projects a {@link MathSubmission} into a DTO suitable for serialization back to the client.
     *
     * @param submission the entity to project
     * @return the DTO carrying the user-facing submission fields (results, participation, content)
     */
    public static MathSubmissionDTO of(MathSubmission submission) {
        List<MathResultDTO> resultDTOs = null;
        List<Result> results = submission.getResults();
        if (results != null && !results.isEmpty()) {
            resultDTOs = results.stream().map(MathResultDTO::of).toList();
        }

        MathParticipationDTO participationDTO = null;
        if (submission.getParticipation() instanceof StudentParticipation sp) {
            participationDTO = MathParticipationDTO.of(sp);
        }

        return new MathSubmissionDTO(submission.getId(), submission.isSubmitted(), submission.getSubmissionDate(), resultDTOs, participationDTO, submission.getContent());
    }

    /**
     * Builds a fresh {@link MathSubmission} entity from this DTO.
     *
     * @return a new entity populated with the DTO's submission fields (id, submitted flag, content)
     */
    public MathSubmission toEntity() {
        MathSubmission submission = new MathSubmission();
        if (id != null) {
            submission.setId(id);
        }
        submission.setSubmitted(Boolean.TRUE.equals(submitted));
        submission.setContent(content);
        return submission;
    }
}
