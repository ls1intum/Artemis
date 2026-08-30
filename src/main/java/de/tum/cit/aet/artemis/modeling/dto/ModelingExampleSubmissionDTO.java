package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;

/**
 * Read DTO for an {@link ExampleSubmission} of a modeling exercise, as exposed on the single modeling-exercise detail
 * endpoint. Carries the example submission with a minimal projection of its (example) submission so the example
 * submission management page can render the diagram size and the "example assessment created" marker.
 * <p>
 * The nested submission projection is intentionally purpose-built and minimal (it does not reference the not-yet-existing
 * {@code ModelingSubmissionResponseDTO}): the management page reads {@code submission.model} (to compute the diagram
 * size) and {@code submission.results[*].exampleResult} (to flag whether an example assessment exists).
 *
 * @param id              the example submission id
 * @param usedForTutorial whether this example submission is used for the tutorial
 * @param submission      the minimal example modeling-submission projection
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingExampleSubmissionDTO(Long id, boolean usedForTutorial, ExampleModelingSubmissionDTO submission) implements Serializable {

    /**
     * Minimal projection of the example {@link ModelingSubmission} the example-submission management page reads.
     *
     * @param id              the submission id
     * @param model           the UML model (diagram-size computation on the management page)
     * @param explanationText the explanation text of the submission
     * @param submitted       whether the submission was submitted
     * @param results         the (example) results; the page reads {@code results[*].exampleResult}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExampleModelingSubmissionDTO(Long id, String model, String explanationText, Boolean submitted, List<ResultDTO> results) implements Serializable {

        /**
         * The example-submission edit page echoes the exercise detail response back into its save PUT, where this
         * projection is deserialized into the abstract {@link de.tum.cit.aet.artemis.exercise.domain.Submission}.
         * That type resolves its subtype from this discriminator, so it must stay on the wire (the entity payload
         * carried it via {@code @JsonTypeInfo}); without it the PUT fails as unreadable.
         *
         * @return the constant submission type discriminator
         */
        @JsonProperty("submissionExerciseType")
        public String submissionExerciseType() {
            return "modeling";
        }
    }

    /**
     * Converts an {@link ExampleSubmission} into a {@link ModelingExampleSubmissionDTO}.
     *
     * @param exampleSubmission the example submission to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ModelingExampleSubmissionDTO of(ExampleSubmission exampleSubmission) {
        if (exampleSubmission == null) {
            return null;
        }
        ExampleModelingSubmissionDTO submission = null;
        if (exampleSubmission.getSubmission() instanceof ModelingSubmission modelingSubmission) {
            List<ResultDTO> results = Hibernate.isInitialized(modelingSubmission.getResults()) && modelingSubmission.getResults() != null
                    ? modelingSubmission.getResults().stream().map(ResultDTO::of).toList()
                    : null;
            submission = new ExampleModelingSubmissionDTO(modelingSubmission.getId(), modelingSubmission.getModel(), modelingSubmission.getExplanationText(),
                    modelingSubmission.isSubmitted(), results);
        }
        return new ModelingExampleSubmissionDTO(exampleSubmission.getId(), exampleSubmission.isUsedForTutorial(), submission);
    }
}
