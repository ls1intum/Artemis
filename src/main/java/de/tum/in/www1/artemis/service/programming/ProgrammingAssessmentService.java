package de.tum.in.www1.artemis.service.programming;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.lti.LtiNewResultService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.websocket.ResultWebsocketService;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService, SubmissionService submissionService,
            SubmissionRepository submissionRepository, ExamDateService examDateService, UserRepository userRepository, GradingCriterionRepository gradingCriterionRepository,
            Optional<LtiNewResultService> ltiNewResultService, SingleUserNotificationService singleUserNotificationService, ResultWebsocketService resultWebsocketService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateService, gradingCriterionRepository, userRepository, ltiNewResultService, singleUserNotificationService, resultWebsocketService);
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to SEMI_AUTOMATIC and sets the assessor attribute.
     * Furthermore, it saves the result in the database.
     *
     * @param result   the new result of a programming exercise
     * @param assessor the user who created the assessment
     * @return result that was saved in the database
     */
    private Result saveManualAssessment(Result result, User assessor) {
        var participation = result.getParticipation();

        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        result.setAssessor(assessor);
        result.setCompletionDate(null);

        Result finalResult = resultService.storeFeedbackInResult(result, result.getFeedbacks(), true);

        finalResult.setParticipation(participation);
        return finalResult;
    }

    /**
     * Saves a new manual assessment. Submits the result if the submit-parameter is set to true.
     * Also notifies the student about the assessment if it is visible (after the assessment due date).
     *
     * @param participation        the participation to add a new result
     * @param newManualResult      the new result (from the client)
     * @param existingManualResult the old existing result (loaded from the database)
     * @param assessor             the author of the assessment
     * @param submit               true if the result should also be submitted
     * @return the new saved result
     */
    public Result saveAndSubmitManualAssessment(StudentParticipation participation, Result newManualResult, Result existingManualResult, User assessor, boolean submit) {
        // make sure that the submission cannot be manipulated on the client side
        var submission = (ProgrammingSubmission) existingManualResult.getSubmission();
        var exercise = participation.getExercise();

        newManualResult.setSubmission(submission);
        newManualResult.setHasComplaint(existingManualResult.getHasComplaint().orElse(false));
        newManualResult = saveManualAssessment(newManualResult, assessor);

        if (submission.getParticipation() == null) {
            newManualResult.setParticipation(submission.getParticipation());
        }
        Result savedResult = resultRepository.save(newManualResult);
        savedResult.setSubmission(submission);

        // Re-load result to fetch the test cases
        newManualResult = resultRepository.findByIdWithEagerSubmissionAndFeedbackAndTestCasesElseThrow(newManualResult.getId());

        if (submit) {
            newManualResult = resultRepository.submitManualAssessment(newManualResult);

            if (submission.getParticipation() instanceof StudentParticipation studentParticipation && studentParticipation.getStudent().isPresent()) {
                singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(exercise, studentParticipation.getStudent().get(), newManualResult);
            }
        }

        // Note: we always need to report the result over LTI, otherwise it might never become visible in the external system
        if (ltiNewResultService.isPresent()) {
            ltiNewResultService.get().onNewResult((StudentParticipation) newManualResult.getParticipation());
        }
        if (submit && ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            resultWebsocketService.broadcastNewResult(newManualResult.getParticipation(), newManualResult);
        }

        handleResolvedFeedbackRequest(participation);
        newManualResult.setParticipation(participation);
        return newManualResult;
    }

    private void handleResolvedFeedbackRequest(StudentParticipation participation) {
        var exercise = participation.getExercise();
        var isManualFeedbackRequest = exercise.getAllowManualFeedbackRequests() && participation.getIndividualDueDate() != null
                && participation.getIndividualDueDate().isBefore(ZonedDateTime.now());
        var isBeforeDueDate = exercise.getDueDate() != null && exercise.getDueDate().isAfter(ZonedDateTime.now());

        if (isManualFeedbackRequest && isBeforeDueDate) {
            participation.setIndividualDueDate(null);
            studentParticipationRepository.save(participation);

            programmingExerciseParticipationService.unlockStudentRepositoryAndParticipation((ProgrammingExerciseStudentParticipation) participation);
        }
    }

}
