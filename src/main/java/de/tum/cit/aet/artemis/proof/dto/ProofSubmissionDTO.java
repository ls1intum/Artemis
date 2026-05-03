package de.tum.cit.aet.artemis.proof.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;

/**
 * Data Transfer Object for {@link ProofSubmission}.
 * <p>
 * Used as both request body (student sends {@code text}, {@code studentCheckboxState}, {@code submitted})
 * and response body (server additionally populates {@code id}, {@code submissionDate}, {@code results}, {@code participation}).
 *
 * @param id                   the submission ID (null for new submissions)
 * @param text                 the proof text written by the student
 * @param studentCheckboxState the student's checkbox answer
 * @param submitted            whether this is a final submission (triggers automatic grading)
 * @param submissionDate       when the submission was last saved (response only)
 * @param results              automatic grading results (response only, populated after submit)
 * @param participation        the student's participation including exercise info (response only)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProofSubmissionDTO(Long id, String text, Boolean studentCheckboxState, Boolean submitted, ZonedDateTime submissionDate, List<ProofResultDTO> results,
        ProofParticipationDTO participation) {

    /**
     * Minimal grading result for the student-facing view.
     *
     * @param id             the result ID
     * @param score          the score (0.0 or 100.0)
     * @param assessmentType the assessment type (AUTOMATIC for proof exercises)
     * @param completionDate when the result was generated
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProofResultDTO(Long id, Double score, AssessmentType assessmentType, ZonedDateTime completionDate) {

        public static ProofResultDTO of(Result result) {
            return new ProofResultDTO(result.getId(), result.getScore(), result.getAssessmentType(), result.getCompletionDate());
        }
    }

    /**
     * Participation summary included in submission responses, exposing the exercise details needed by the student view.
     *
     * @param id       the participation ID
     * @param exercise the proof exercise associated with this participation
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProofParticipationDTO(Long id, ProofExerciseDTO exercise) {

        public static ProofParticipationDTO of(StudentParticipation participation) {
            ProofExerciseDTO exerciseDTO = null;
            if (participation.getExercise() instanceof ProofExercise pe) {
                exerciseDTO = ProofExerciseDTO.of(pe);
            }
            return new ProofParticipationDTO(participation.getId(), exerciseDTO);
        }
    }

    /**
     * Creates a response DTO from a saved {@link ProofSubmission} entity.
     * Participation and results are included only when initialised on the entity.
     *
     * @param submission the entity to convert
     * @return a populated response DTO
     */
    public static ProofSubmissionDTO of(ProofSubmission submission) {
        List<ProofResultDTO> resultDTOs = null;
        List<Result> results = submission.getResults();
        if (results != null && !results.isEmpty()) {
            resultDTOs = results.stream().map(ProofResultDTO::of).toList();
        }

        ProofParticipationDTO participationDTO = null;
        if (submission.getParticipation() instanceof StudentParticipation sp) {
            participationDTO = ProofParticipationDTO.of(sp);
        }

        return new ProofSubmissionDTO(submission.getId(), submission.getText(), submission.isStudentCheckboxState(), submission.isSubmitted(), submission.getSubmissionDate(),
                resultDTOs, participationDTO);
    }

    /**
     * Converts the request fields of this DTO into a new {@link ProofSubmission} entity.
     * Participation is not set here — the caller must set it before persisting.
     *
     * @return a new ProofSubmission populated from this DTO
     */
    public ProofSubmission toEntity() {
        ProofSubmission submission = new ProofSubmission();
        if (id != null) {
            submission.setId(id);
        }
        submission.setText(text);
        submission.setStudentCheckboxState(studentCheckboxState != null ? studentCheckboxState : Boolean.FALSE);
        submission.setSubmitted(Boolean.TRUE.equals(submitted));
        return submission;
    }
}
