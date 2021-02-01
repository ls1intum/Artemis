package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    private final UserService userService;

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService, SubmissionService submissionService,
            SubmissionRepository submissionRepository, ExamService examService, UserService userService, GradingCriterionService gradingCriterionService, LtiService ltiService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examService, gradingCriterionService, userService, ltiService);
        this.userService = userService;
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to SEMI_AUTOMATIC and sets the assessor attribute.
     * Furthermore, it saves the result in the database.
     *
     * @param result the new result of a programming exercise
     * @return result that was saved in the database
     */
    public Result saveManualAssessment(Result result) {
        result.setHasFeedback(!result.getFeedbacks().isEmpty());
        var participation = result.getParticipation();
        User user = userService.getUserWithGroupsAndAuthorities();

        result.setHasComplaint(false);
        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        result.setAssessor(user);
        result.setCompletionDate(null);

        // Avoid hibernate exception
        List<Feedback> savedFeedbacks = new ArrayList<>();
        result.getFeedbacks().forEach(feedback -> {
            feedback.setResult(null);
            feedback = feedbackRepository.save(feedback);
            feedback.setResult(result);
            savedFeedbacks.add(feedback);
        });

        Result finalResult = result;
        finalResult.setFeedbacks(savedFeedbacks);
        // Note: This also saves the feedback objects in the database because of the 'cascade = CascadeType.ALL' option.
        finalResult = resultRepository.save(finalResult);
        finalResult.setParticipation(participation);
        return finalResult;
    }

    /**
     * This function is used for submitting a manual assessment/result. It gets the result that belongs to the given resultId, updates the completion date.
     * It saves the updated result in the database again.
     *
     * @param resultId the id of the result that should be submitted
     * @return the ResponseEntity with result as body
     */
    public Result submitManualAssessment(long resultId) {
        Result result = resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("No result for the given resultId could be found"));
        result.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result);
        return result;
    }

    /**
     * Calculates the total score for programming exercises.
     *
     * @param result with information about feedback and exercise
     * @return calculated totalPoints
     */
    public Double calculateTotalPoints(Result result) {
        double totalPoints = 0.0;
        double scoreAutomaticTests = 0.0;
        ProgrammingExercise programmingExercise = (ProgrammingExercise) result.getParticipation().getExercise();
        List<Feedback> assessments = result.getFeedbacks();
        var gradingInstructions = new HashMap<Long, Integer>(); // { instructionId: noOfEncounters }

        for (Feedback feedback : assessments) {
            if (feedback.getGradingInstruction() != null) {
                totalPoints = gradingCriterionService.computeTotalPoints(feedback, totalPoints, gradingInstructions);
            }
            else {
                /*
                 * In case no structured grading instruction was applied on the assessment model we just sum the feedback credit. We differentiate between automatic test and
                 * automatic SCA feedback (automatic test feedback has to be capped)
                 */
                if (feedback.getType() == FeedbackType.AUTOMATIC && !feedback.isStaticCodeAnalysisFeedback()) {
                    scoreAutomaticTests += feedback.getCredits();
                }
                else {
                    totalPoints += feedback.getCredits();
                }
            }
        }
        /** Calculated points from automatic test feedbacks, is capped to max points + bonus points,
         * see also see {@link ProgrammingExerciseGradingService#updateScore} */
        double maxPoints = programmingExercise.getMaxPoints() + Optional.ofNullable(programmingExercise.getBonusPoints()).orElse(0.0);
        if (scoreAutomaticTests > maxPoints) {
            scoreAutomaticTests = maxPoints;
        }
        totalPoints += scoreAutomaticTests;
        // Make sure to not give negative points
        if (totalPoints < 0) {
            totalPoints = 0;
        }
        // Make sure to not give more than maxPoints
        if (totalPoints > maxPoints) {
            totalPoints = maxPoints;
        }
        return totalPoints;
    }
}
