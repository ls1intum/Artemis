package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    private final UserService userService;

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            SubmissionRepository submissionRepository, ExamService examService, UserService userService, AchievementService achievementService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionRepository, examService,
                achievementService);
        this.userService = userService;
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the
     * database.
     *
     * @param result the new result of a programming exercise
     * @return result that was saved in the database
     */
    @Transactional
    public Result saveManualAssessment(Result result) {
        result.setHasFeedback(!result.getFeedbacks().isEmpty());

        User user = userService.getUserWithGroupsAndAuthorities();
        result.setHasComplaint(false);
        result.setAssessmentType(AssessmentType.MANUAL);
        result.setAssessor(user);

        result.setCompletionDate(null);
        result.setRated(false);

        result.getFeedbacks().forEach(feedback -> {
            feedback.setResult(result);
        });

        // Note: This also saves the feedback objects in the database because of the 'cascade = CascadeType.ALL' option.
        return resultRepository.save(result);
    }

    /**
     * This function is used for submitting a manual assessment/result. It gets the result that belongs to the given resultId, updates the completion date, sets the assessment type
     * to MANUAL and sets the assessor attribute. Afterwards, it saves the update result in the database again.
     *
     * @param resultId the id of the result that should be submitted
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitManualAssessment(long resultId) {
        Result result = resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("No result for the given resultId could be found"));

        Double calculatedScore = calculateTotalScore(result.getFeedbacks());
        return submitResult(result, result.getParticipation().getExercise(), calculatedScore);
    }
}
