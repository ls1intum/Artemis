package de.tum.in.www1.artemis.service.programming;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaFeedbackSendingService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiNewResultService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.websocket.ResultWebsocketService;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final Optional<AthenaFeedbackSendingService> athenaFeedbackSendingService;

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService, SubmissionService submissionService,
            SubmissionRepository submissionRepository, ExamDateService examDateService, UserRepository userRepository, GradingCriterionRepository gradingCriterionRepository,
            Optional<LtiNewResultService> ltiNewResultService, SingleUserNotificationService singleUserNotificationService, ResultWebsocketService resultWebsocketService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, Optional<AthenaFeedbackSendingService> athenaFeedbackSendingService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateService, gradingCriterionRepository, userRepository, ltiNewResultService, singleUserNotificationService, resultWebsocketService);
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.athenaFeedbackSendingService = athenaFeedbackSendingService;
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
        ProgrammingExercise exercise = (ProgrammingExercise) participation.getExercise();

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
            return submitManualAssessment(newManualResult, submission, participation, exercise);
        }
        return newManualResult;
    }

    private Result submitManualAssessment(Result newManualResult, ProgrammingSubmission submission, StudentParticipation participation, ProgrammingExercise exercise) {
        newManualResult = resultRepository.submitManualAssessment(newManualResult);

        if (participation.getStudent().isPresent()) {
            singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(exercise, participation.getStudent().get(), newManualResult);
        }

        // Note: we always need to report the result over LTI, even if the assessment due date is not over yet.
        // Otherwise it might never become visible in the external system
        if (ltiNewResultService.isPresent()) {
            ltiNewResultService.get().onNewResult((StudentParticipation) newManualResult.getParticipation());
        }
        if (ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            resultWebsocketService.broadcastNewResult(newManualResult.getParticipation(), newManualResult);
        }

        sendFeedbackToAthena(exercise, submission, newManualResult.getFeedbacks());
        handleResolvedFeedbackRequest(participation);
        newManualResult.setParticipation(participation);

        return newManualResult;
    }

    /**
     * Send feedback to Athena (if enabled for both the Artemis instance and the exercise).
     */
    private void sendFeedbackToAthena(final ProgrammingExercise exercise, final ProgrammingSubmission programmingSubmission, final List<Feedback> feedbacks) {
        if (athenaFeedbackSendingService.isPresent() && exercise.getFeedbackSuggestionsEnabled()) {
            athenaFeedbackSendingService.get().sendFeedback(exercise, programmingSubmission, feedbacks);
        }
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
