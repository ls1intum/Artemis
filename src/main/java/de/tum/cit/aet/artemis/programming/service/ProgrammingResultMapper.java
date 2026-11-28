package de.tum.cit.aet.artemis.programming.service;

import java.util.Collection;
import java.util.List;

import org.hibernate.Hibernate;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.exercise.service.ParticipationMapper;
import de.tum.cit.aet.artemis.exercise.service.SubmissionMapper;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO.FeedbackDTO;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO.TestCaseDTO;

/** Maps persisted data to the records exchanged with clients and build agents. */
public final class ProgrammingResultMapper {

    private ProgrammingResultMapper() {
    }

    private static FeedbackDTO feedback(Feedback feedback) {
        return new FeedbackDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getHasLongFeedbackText(), feedback.getReference(), feedback.getCredits(),
                feedback.isPositive(), feedback.getType(), feedback.getVisibility(), testCase(feedback.getTestCase()));
    }

    public static TestCaseDTO testCase(ProgrammingExerciseTestCase testCase) {
        if (testCase == null) {
            return null;
        }
        return new TestCaseDTO(testCase.getTestName(), testCase.getId());
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
    public static ResultDTO of(Result result, Collection<Feedback> filteredFeedback) {
        SubmissionDTO submissionDTO = null;
        // Hibernate.isInitialized(null) is true, so the null check has to stand next to it.
        if (result.getSubmission() != null && Hibernate.isInitialized(result.getSubmission())) {
            submissionDTO = SubmissionMapper.of(result.getSubmission(), false, null, null);
        }
        var feedbackDTOs = filteredFeedback.stream().map(ProgrammingResultMapper::feedback).toList();
        return new ResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), submissionDTO,
                ParticipationMapper.of(result.getSubmission().getParticipation()), feedbackDTOs, result.getAssessmentType(), result.getCorrectionRound(), result.hasComplaint(),
                result.isExampleResult(), result.getTestCaseCount(), result.getPassedTestCaseCount(), result.getCodeIssueCount());
    }

    /**
     * Converts a Result that is serialized <em>nested under its own submission</em> into a ResultDTO.
     * <p>
     * Both {@code submission} and {@code participation} are left {@code null}: the entity wire suppresses them via
     * {@code @JsonIgnoreProperties({"submission", "participation"})} on {@code Submission.results}, and re-emitting
     * them would repeat the whole submission/participation/exercise/course subtree once per result. Unlike
     * {@link #of(Result, Collection)} this never dereferences {@code result.getSubmission()}, so a result with a
     * {@code null} submission maps without a {@link NullPointerException}.
     * <p>
     * The feedback collection is only mapped when it is already initialized, so this never triggers a lazy load.
     *
     * @param result the result to convert
     * @return the converted DTO
     */
    public static ResultDTO ofNested(Result result) {
        var feedbacks = result.getFeedbacks();
        return ofNested(result, feedbacks != null && Hibernate.isInitialized(feedbacks) ? feedbacks : null);
    }

    /**
     * Converts a Result that is serialized <em>nested under its own submission</em> into a ResultDTO, with an
     * explicit feedback list. Callers that filter feedback (sensitive-information filtering) use this overload so
     * they never have to mutate the managed result to shape the JSON; callers that just want whatever is loaded use
     * {@link #ofNested(Result)}.
     *
     * @param result           the result to convert
     * @param filteredFeedback the feedback that should be sent to the client; {@code null} maps to no feedback list
     * @return the converted DTO
     */
    public static ResultDTO ofNested(Result result, Collection<Feedback> filteredFeedback) {
        List<FeedbackDTO> feedbackDTOs = filteredFeedback == null ? null : filteredFeedback.stream().map(ProgrammingResultMapper::feedback).toList();
        return new ResultDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), null, null, feedbackDTOs,
                result.getAssessmentType(), result.getCorrectionRound(), result.hasComplaint(), result.isExampleResult(), result.getTestCaseCount(),
                result.getPassedTestCaseCount(), result.getCodeIssueCount());
    }
}
