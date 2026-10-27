package de.tum.cit.aet.artemis.assessment.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;

/**
 * DTO containing {@link Feedback} information.
 * This does not include large reference attributes (e.g. long feedback text) in order to send minimal data to the client.
 * An empty detail text is kept: the client reads it as a title in {@code text} without a description, unlike legacy feedback without one.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDTO(Long id, String text, @JsonInclude(JsonInclude.Include.NON_NULL) String detailText, boolean hasLongFeedbackText, String reference, Double credits,
        Boolean positive, FeedbackType type, Visibility visibility, GradingInstructionDTO gradingInstruction, TestCaseDTO testCase) implements Serializable {

    /**
     * The test case an automatic programming feedback belongs to. The components are every scalar property
     * {@link ProgrammingExerciseTestCase} serialized at this position before the migration: the entity carried
     * {@code @JsonIgnoreProperties({ "tasks", "exercise" })}, so only those two associations were left out.
     * The Artemis client reads the id and the name, the route is also served to the SCORPIO extension.
     *
     * @param id              the id of the test case
     * @param testName        the name of the test case
     * @param weight          the weight of the test case
     * @param active          whether the test case counts towards the score
     * @param visibility      when the test case is shown to students
     * @param bonusMultiplier the bonus multiplier of the test case
     * @param bonusPoints     the bonus points of the test case
     * @param type            the type of the test case
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestCaseDTO(Long id, String testName, Double weight, Boolean active, Visibility visibility, Double bonusMultiplier, Double bonusPoints,
            ProgrammingExerciseTestCaseType type) implements Serializable {

        /**
         * Converts a test case into a TestCaseDTO.
         *
         * @param testCase to convert
         * @return the converted DTO, or null if the test case is null
         */
        public static TestCaseDTO of(ProgrammingExerciseTestCase testCase) {
            if (testCase == null) {
                return null;
            }
            return new TestCaseDTO(testCase.getId(), testCase.getTestName(), testCase.getWeight(), testCase.isActive(), testCase.getVisibility(), testCase.getBonusMultiplier(),
                    testCase.getBonusPoints(), testCase.getType());
        }
    }

    /**
     * Rebuilds the editable assessment feedback, preserving its id and instruction reference. The caller resolves long
     * text before mapping, because modeling loads it in bulk and text assessments load it individually.
     *
     * @param resolvedDetailText full detail text, including any restored stored long text
     * @return transient feedback carrying the assessment update
     */
    public Feedback toAssessmentFeedback(String resolvedDetailText) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setCredits(credits);
        feedback.setDetailText(resolvedDetailText);
        feedback.setText(text);
        feedback.setReference(reference);
        feedback.setType(type);
        feedback.setPositive(positive);
        feedback.setVisibility(visibility);
        if (gradingInstruction != null && gradingInstruction.id() != null) {
            GradingInstruction instruction = new GradingInstruction();
            instruction.setId(gradingInstruction.id());
            feedback.setGradingInstruction(instruction);
        }
        return feedback;
    }

    /**
     * Converts a Feedback into a FeedbackDTO.
     *
     * @param feedback to convert
     * @return the converted DTO, or null if the feedback is null
     */
    public static FeedbackDTO of(Feedback feedback) {
        if (feedback == null) {
            return null;
        }
        GradingInstructionDTO gradingInstruction = feedback.getGradingInstruction() != null ? GradingInstructionDTO.of(feedback.getGradingInstruction()) : null;
        // the test case is a transient field set on synthesized automatic feedback views, never a lazy association
        return new FeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(), feedback.getCredits(),
                feedback.isPositive(), feedback.getType(), feedback.getVisibility(), gradingInstruction, TestCaseDTO.of(feedback.getTestCase()));
    }
}
