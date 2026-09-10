package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentNoteDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;

/**
 * A {@link Result} as the manual-assessment surfaces serialize it.
 * <p>
 * This is deliberately NOT {@code assessment.dto.ResultDTO}: that record's feedback projection has no {@code testCase}
 * component, while the tutor assessment editor echoes the automatic feedback it received straight back into the
 * manual-results write. Since that write replaces {@code Result.feedbacks} (cascade ALL + orphanRemoval), a response
 * without {@code testCase} would drop the test-case link of every automatic feedback on the first save. It is also not
 * {@code programming.dto.ResultDTO}: the assessment surfaces additionally need the assessor (the editor decides
 * "am I the assessor?" from it), the assessment note and the full nested grading instruction (the client recomputes
 * the score from {@code gradingInstruction.credits} / {@code usageCount}; a flat id silently mis-computes it).
 * <p>
 * Every sub-graph is mapped only when it is already loaded: results are not fetched uniformly across the endpoints
 * that use this record (the unlocked dashboard query deliberately omits feedback and test cases), and open-in-view is
 * disabled, so an unguarded dereference would throw instead of producing today's "absent" wire shape.
 *
 * @param id                  the result id
 * @param completionDate      when the assessment was submitted; {@code null} while the assessment is only saved
 * @param successful          whether the result is successful
 * @param score               the relative score in percent
 * @param rated               whether the result counts towards the student's score
 * @param assessmentType      automatic, semi-automatic or manual
 * @param correctionRound     which correction round this result belongs to; {@code null} for automatic results
 * @param hasComplaint        whether a complaint exists for this result
 * @param exampleResult       whether this result belongs to an example submission
 * @param testCaseCount       the number of executed test cases
 * @param passedTestCaseCount the number of passed test cases
 * @param codeIssueCount      the number of static code analysis issues
 * @param assessor            the assessor; {@code null} when it is not loaded
 * @param assessmentNote      the internal assessment note; {@code null} when there is none
 * @param feedbacks           the feedback items; {@code null} when they are not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingAssessmentResultDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, AssessmentType assessmentType,
        Integer correctionRound, Boolean hasComplaint, Boolean exampleResult, Integer testCaseCount, Integer passedTestCaseCount, Integer codeIssueCount, UserNameDTO assessor,
        AssessmentNoteDTO assessmentNote, List<ProgrammingAssessmentFeedbackDTO> feedbacks) implements Serializable {

    /**
     * A {@link Feedback} as the manual-assessment surfaces serialize it.
     * <p>
     * The union of what the tutor editor reads AND what it echoes back on save: {@code testCase} keeps the automatic
     * feedback's test-case link alive across the round trip, {@code gradingInstruction} is a nested object because the
     * client's score computation reads its credits and usage count, and {@code hasLongFeedbackText} is what tells the
     * server on the way back that an existing long feedback text must be re-attached rather than deleted.
     *
     * @param id                  the feedback id
     * @param text                the short feedback text (test case name for automatic feedback)
     * @param detailText          the detail text, or its preview when a long feedback text exists
     * @param hasLongFeedbackText whether the detail text is stored in a separate long-feedback row
     * @param reference           the reference to the assessed element (file and line for programming exercises)
     * @param credits             the points awarded by this feedback
     * @param positive            whether the feedback is positive
     * @param type                automatic, manual, unreferenced manual or adapted
     * @param visibility          when the feedback becomes visible to the student
     * @param gradingInstruction  the structured grading instruction this feedback was created from
     * @param testCase            the test case that created this feedback; {@code null} for manual feedback
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProgrammingAssessmentFeedbackDTO(Long id, String text, String detailText, boolean hasLongFeedbackText, String reference, Double credits, Boolean positive,
            FeedbackType type, Visibility visibility, GradingInstructionDTO gradingInstruction, ResultDTO.TestCaseDTO testCase) implements Serializable {

        /**
         * Converts a feedback item. The lazy {@code testCase} and {@code gradingInstruction} slots are mapped only
         * when they are already loaded, so this never triggers a lazy load.
         *
         * @param feedback the feedback to convert
         * @return the converted DTO
         */
        public static ProgrammingAssessmentFeedbackDTO of(Feedback feedback) {
            GradingInstruction gradingInstruction = feedback.getGradingInstruction();
            GradingInstructionDTO gradingInstructionDTO = gradingInstruction != null && Hibernate.isInitialized(gradingInstruction) ? GradingInstructionDTO.of(gradingInstruction)
                    : null;
            var testCase = feedback.getTestCase();
            ResultDTO.TestCaseDTO testCaseDTO = testCase != null && Hibernate.isInitialized(testCase) ? ResultDTO.TestCaseDTO.of(testCase) : null;
            return new ProgrammingAssessmentFeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(),
                    feedback.getCredits(), feedback.isPositive(), feedback.getType(), feedback.getVisibility(), gradingInstructionDTO, testCaseDTO);
        }
    }

    /**
     * Converts a result including every already-loaded sub-graph.
     *
     * @param result the result to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingAssessmentResultDTO of(Result result) {
        if (result == null) {
            return null;
        }
        // Hibernate.isInitialized(null) is true, so every guard below needs its own null check.
        List<ProgrammingAssessmentFeedbackDTO> feedbacks = null;
        if (result.getFeedbacks() != null && Hibernate.isInitialized(result.getFeedbacks())) {
            // The entity stores feedback in an unordered Set; sort by id so the wire order is stable for the client
            // (and for tests that assert on positions), keeping items without an id last.
            feedbacks = result.getFeedbacks().stream().filter(Objects::nonNull).sorted(Comparator.comparing(Feedback::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(ProgrammingAssessmentFeedbackDTO::of).toList();
        }
        var assessorEntity = result.getAssessor();
        UserNameDTO assessor = assessorEntity != null && Hibernate.isInitialized(assessorEntity) ? UserNameDTO.of(assessorEntity) : null;
        // Result#getAssessmentNote already guards the lazy backing list itself.
        AssessmentNoteDTO assessmentNote = AssessmentNoteDTO.of(result.getAssessmentNote());
        return new ProgrammingAssessmentResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(),
                result.getAssessmentType(), result.getCorrectionRound(), result.hasComplaint(), result.isExampleResult(), result.getTestCaseCount(),
                result.getPassedTestCaseCount(), result.getCodeIssueCount(), assessor, assessmentNote, feedbacks);
    }
}
