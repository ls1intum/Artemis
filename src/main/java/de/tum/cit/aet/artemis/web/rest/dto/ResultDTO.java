package de.tum.cit.aet.artemis.web.rest.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;

/**
 * DTO containing {@link Result} information.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
// TODO: the result should include an actual string calculated and rounded on server side (based on exercise and course settings), this should not be done on the client side
// this would also simplify the logic in result.component.ts and and result.service.ts and make the experience more consistent among different clients (webapp, ios, android)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, SubmissionDTO submission, ParticipationDTO participation,
        List<FeedbackDTO> feedbacks, AssessmentType assessmentType, Boolean hasComplaint, Boolean exampleResult, Integer testCaseCount, Integer passedTestCaseCount,
        Integer codeIssueCount) implements Serializable {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FeedbackDTO(Long id, String text, String detailText, boolean hasLongFeedbackText, String reference, Double credits, Boolean positive, FeedbackType type,
            Visibility visibility, TestCaseDTO testCase) implements Serializable {

        public static FeedbackDTO of(Feedback feedback) {
            return new FeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(),
                    feedback.getCredits(), feedback.isPositive(), feedback.getType(), feedback.getVisibility(), TestCaseDTO.of(feedback.getTestCase()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestCaseDTO(String testName, Long id) implements Serializable {

        public static TestCaseDTO of(ProgrammingExerciseTestCase testCase) {
            if (testCase == null) {
                return null;
            }
            return new TestCaseDTO(testCase.getTestName(), testCase.getId());
        }
    }

    public static ResultDTO of(Result result) {
        return of(result, result.getFeedbacks());
    }

    /**
     * Converts a Result into a ResultDTO
     *
     * @param result           to convert
     * @param filteredFeedback feedback that should get send to the client, will get converted into {@link FeedbackDTO} objects.
     * @return the converted DTO
     */
    public static ResultDTO of(Result result, List<Feedback> filteredFeedback) {
        SubmissionDTO submissionDTO = null;
        if (Hibernate.isInitialized(result.getSubmission()) && result.getSubmission() != null) {
            submissionDTO = SubmissionDTO.of(result.getSubmission());
        }
        var feedbackDTOs = filteredFeedback.stream().map(FeedbackDTO::of).toList();
        return new ResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), submissionDTO,
                ParticipationDTO.of(result.getParticipation()), feedbackDTOs, result.getAssessmentType(), result.hasComplaint(), result.isExampleResult(),
                result.getTestCaseCount(), result.getPassedTestCaseCount(), result.getCodeIssueCount());
    }
}
