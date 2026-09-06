package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;

/**
 * Read DTO for a {@link ModelingSubmission} returned to the client.
 * <p>
 * The submission stays at the top level of the response (the modeling client reads {@code submission.model},
 * {@code submission.results} and {@code submission.participation} directly and rebuilds
 * {@code participation.submissions = [submission]} itself). Lazy associations (results, participation) are guarded with
 * {@link Hibernate#isInitialized} so uninitialized proxies map to {@code null} rather than triggering a lazy load. The
 * caller is expected to have masked/filtered the entity (result filtering, {@code hideDetails},
 * {@code filterSensitiveInformation}, anonymization) before invoking the factory.
 *
 * @param id                     the submission id
 * @param submissionExerciseType the submission exercise type marker ({@code "modeling"})
 * @param model                  the UML model JSON
 * @param explanationText        the explanation text
 * @param submitted              whether the submission was submitted
 * @param submissionDate         the submission date (nulled for the anonymized student branch)
 * @param type                   the submission type (MANUAL, ...)
 * @param exampleSubmission      whether this is an example submission
 * @param participation          the owning participation (nulled for the anonymized student branch)
 * @param results                the (already filtered, ordered) results
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingSubmissionResponseDTO(Long id, String submissionExerciseType, String model, String explanationText, Boolean submitted, ZonedDateTime submissionDate,
        SubmissionType type, Boolean exampleSubmission, ModelingParticipationDTO participation, List<ResultDTO> results) implements Serializable {

    /**
     * Converts a {@link ModelingSubmission} into a {@link ModelingSubmissionResponseDTO} without the participation owner.
     *
     * @param submission the submission to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the submission is {@code null}
     */
    public static ModelingSubmissionResponseDTO of(ModelingSubmission submission) {
        return of(submission, false);
    }

    /**
     * Converts a {@link ModelingSubmission} into a {@link ModelingSubmissionResponseDTO}.
     *
     * @param submission     the submission to convert (may be {@code null})
     * @param includeStudent whether the participation owner (student/team) should be included
     * @return the converted DTO, or {@code null} if the submission is {@code null}
     */
    public static ModelingSubmissionResponseDTO of(ModelingSubmission submission, boolean includeStudent) {
        if (submission == null) {
            return null;
        }

        List<ResultDTO> results = null;
        if (submission.getResults() != null && Hibernate.isInitialized(submission.getResults())) {
            // No null slots: the results used to be an ordered list padded with nulls so that the client could read
            // results[correctionRound], and the round now lives on the result itself, so the client matches on it.
            results = submission.getResults().stream().filter(Objects::nonNull).map(ResultDTO::of).toList();
        }

        ModelingParticipationDTO participation = null;
        if (Hibernate.isInitialized(submission.getParticipation()) && submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            participation = ModelingParticipationDTO.of(studentParticipation, includeStudent);
        }

        return new ModelingSubmissionResponseDTO(submission.getId(), submission.getSubmissionExerciseType(), submission.getModel(), submission.getExplanationText(),
                submission.isSubmitted(), submission.getSubmissionDate(), submission.getType(), submission.isExampleSubmission(), participation, results);
    }
}
