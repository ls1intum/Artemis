package de.tum.cit.aet.artemis.exam.dto.conduction;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Polymorphic projection of a {@link Submission} in the conduction payload. The common fields live in
 * {@link SubmissionBaseForConductionDTO}; the per-type content (only one of which is non-null) is unwrapped so the wire
 * stays flat and byte-compatible with the entity payload the (unchanged) client model deserializes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionForConductionDTO(@JsonUnwrapped SubmissionBaseForConductionDTO base, @Nullable @JsonUnwrapped TextSubmissionFieldsForConductionDTO textSubmission,
        @Nullable @JsonUnwrapped ModelingSubmissionFieldsForConductionDTO modelingSubmission, @Nullable @JsonUnwrapped QuizSubmissionFieldsForConductionDTO quizSubmission,
        @Nullable @JsonUnwrapped FileUploadSubmissionFieldsForConductionDTO fileUploadSubmission,
        @Nullable @JsonUnwrapped ProgrammingSubmissionFieldsForConductionDTO programmingSubmission) {

    /**
     * Converts a Submission into a SubmissionForConductionDTO, dispatching on the concrete submission type for the
     * per-type content.
     *
     * @param submission the submission to convert
     * @return the converted DTO, or null if the submission is null
     */
    public static SubmissionForConductionDTO of(Submission submission) {
        if (submission == null) {
            return null;
        }
        TextSubmissionFieldsForConductionDTO textSubmission = null;
        ModelingSubmissionFieldsForConductionDTO modelingSubmission = null;
        QuizSubmissionFieldsForConductionDTO quizSubmission = null;
        FileUploadSubmissionFieldsForConductionDTO fileUploadSubmission = null;
        ProgrammingSubmissionFieldsForConductionDTO programmingSubmission = null;
        switch (submission) {
            case TextSubmission text -> textSubmission = new TextSubmissionFieldsForConductionDTO(text.getText());
            case ModelingSubmission modeling -> modelingSubmission = new ModelingSubmissionFieldsForConductionDTO(modeling.getModel(), modeling.getExplanationText());
            case QuizSubmission quiz -> quizSubmission = QuizSubmissionFieldsForConductionDTO.of(quiz);
            case FileUploadSubmission fileUpload -> fileUploadSubmission = new FileUploadSubmissionFieldsForConductionDTO(fileUpload.getFilePath());
            case ProgrammingSubmission programming -> programmingSubmission = ProgrammingSubmissionFieldsForConductionDTO.of(programming);
            default -> {
                // no additional conduction content for other submission types
            }
        }
        return new SubmissionForConductionDTO(SubmissionBaseForConductionDTO.of(submission), textSubmission, modelingSubmission, quizSubmission, fileUploadSubmission,
                programmingSubmission);
    }
}
