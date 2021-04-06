package de.tum.in.www1.artemis.service.programming;

import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService, SubmissionService submissionService,
            SubmissionRepository submissionRepository, ExamDateService examDateService, UserRepository userRepository, GradingCriterionRepository gradingCriterionRepository,
            LtiService ltiService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateService, gradingCriterionRepository, userRepository, ltiService);
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
        User user = userRepository.getUserWithGroupsAndAuthorities();

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
     * Calculates the total score for programming exercises.
     * @param result with information about feedback and exercise
     * @return calculated totalScore
     */
    public Double calculateTotalScore(Result result) {
        double totalScore = 0.0;
        double scoreAutomaticTests = 0.0;
        ProgrammingExercise programmingExercise = (ProgrammingExercise) result.getParticipation().getExercise();
        List<Feedback> assessments = result.getFeedbacks();
        var gradingInstructions = new HashMap<Long, Integer>(); // { instructionId: noOfEncounters }

        for (Feedback feedback : assessments) {
            if (feedback.getGradingInstruction() != null) {
                totalScore = feedback.computeTotalScore(totalScore, gradingInstructions);
            }
            else {
                /*
                 * In case no structured grading instruction was applied on the assessment model we just sum the feedback credit. We differentiate between automatic test and
                 * automatic SCA feedback (automatic test feedback has to be capped)
                 */
                if (feedback.getType() == FeedbackType.AUTOMATIC && !feedback.isStaticCodeAnalysisFeedback()) {
                    scoreAutomaticTests += Objects.requireNonNullElse(feedback.getCredits(), 0.0);
                }
                else {
                    totalScore += Objects.requireNonNullElse(feedback.getCredits(), 0.0);
                }
            }
        }
        /*
         * Calculated score from automatic test feedbacks, is capped to max points + bonus points, see also see {@link ProgrammingExerciseGradingService#updateScore}
         */
        double maxPoints = programmingExercise.getMaxPoints() + Optional.ofNullable(programmingExercise.getBonusPoints()).orElse(0.0);
        if (scoreAutomaticTests > maxPoints) {
            scoreAutomaticTests = maxPoints;
        }
        totalScore += scoreAutomaticTests;
        // Make sure to not give negative points
        if (totalScore < 0) {
            totalScore = 0;
        }
        // Make sure to not give more than maxPoints
        if (totalScore > maxPoints) {
            totalScore = maxPoints;
        }
        return totalScore;
    }
}
