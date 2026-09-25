package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.service.ComplaintResponseService;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.notification.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingAssessmentService extends AssessmentService {

    private final Optional<AthenaFeedbackApi> athenaFeedbackApi;

    private final TestCasePointsService testCasePointsService;

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    private final ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService;

    private final CourseAthenaConfigRepository courseAthenaConfigRepository;

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService, SubmissionService submissionService,
            SubmissionRepository submissionRepository, Optional<ExamDateApi> examDateApi, UserRepository userRepository, Optional<LtiApi> ltiApi,
            SingleUserNotificationService singleUserNotificationService, ResultWebsocketService resultWebsocketService, Optional<AthenaFeedbackApi> athenaFeedbackApi,
            TestCasePointsService testCasePointsService, TestCaseFeedbackRepository testCaseFeedbackRepository, ScaFeedbackRepository scaFeedbackRepository,
            ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService, CourseAthenaConfigRepository courseAthenaConfigRepository) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateApi, userRepository, ltiApi, singleUserNotificationService, resultWebsocketService);
        this.courseAthenaConfigRepository = courseAthenaConfigRepository;
        this.athenaFeedbackApi = athenaFeedbackApi;
        this.testCasePointsService = testCasePointsService;
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
        this.programmingFeedbackSynthesizerService = programmingFeedbackSynthesizerService;
    }

    @Override
    protected Map<Long, Double> calculateTestCasePoints(ProgrammingExercise exercise, Result result) {
        return testCasePointsService.calculateTestCasePoints(exercise, result);
    }

    @Override
    protected void attachSynthesizedAutomaticFeedback(ProgrammingExercise exercise, Result result) {
        // a complaint always concerns a student participation, never the solution participation
        programmingFeedbackSynthesizerService.attachSynthesizedFeedback(result, exercise, false);
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

        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        result.setAssessor(assessor);
        result.setCompletionDate(null);

        return resultService.storeFeedbackInResult(result, result.getFeedbacks(), true);
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
        AssessmentNote assessmentNote = newManualResult.getAssessmentNote();
        if (assessmentNote != null) {
            assessmentNote.setCreator(assessor);
            newManualResult.setAssessmentNote(assessmentNote);
        }

        newManualResult.setSubmission(submission);
        newManualResult.setExerciseId(exercise.getId());
        newManualResult.setHasComplaint(existingManualResult.getHasComplaint().orElse(false));

        // The client echoes the automatic test-case and SCA feedback items it received (synthesized from the
        // typed collections, hence without ids). They must not be persisted as manual feedback rows - the
        // typed rows on the result already hold them.
        // Stored automatic feedback rows (e.g. submission policy penalties, duplicate test warnings) are written by the server and
        // not editable in the assessment editor, so the echoed copies are dropped as well and the stored rows are kept below.
        // A new build may have replaced those rows since the assessment was loaded, so the echoed ids can be outdated.
        newManualResult.getFeedbacks().removeIf(feedback -> (feedback.getType() == FeedbackType.AUTOMATIC && feedback.getId() != null && feedback.getId() > 0)
                || ((feedback.getId() == null || feedback.getId() < 0) && (feedback.getTestCase() != null || feedback.isStaticCodeAnalysisFeedback())));
        // what is left carries the ids of the manual feedback the tutor loaded for this result, so the stored result decides which ids may be written
        checkFeedbackBelongsToResultElseThrow(newManualResult.getFeedbacks(), existingManualResult);
        keepStoredAutomaticFeedback(newManualResult, existingManualResult);
        // The client-built result has empty typed collections; hydrate them from the database so that
        // saving the result does not orphan-remove the stored typed automatic feedback.
        if (newManualResult.getId() != null) {
            newManualResult.setTestCaseFeedbacks(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(newManualResult.getId())));
            newManualResult.setScaFeedbacks(scaFeedbackRepository.findByResultIds(List.of(newManualResult.getId())));
        }

        newManualResult = saveManualAssessment(newManualResult, assessor);

        Result savedResult = resultRepository.save(newManualResult);
        savedResult.setSubmission(submission);

        // Re-load result to fetch the test cases
        newManualResult = resultRepository.findByIdWithEagerSubmissionAndFeedbackAndAssessmentNoteElseThrow(newManualResult.getId());

        if (submit) {
            Result submittedResult = submitManualAssessment(newManualResult, submission, participation, exercise);
            // the automatic test-case and SCA feedback lives in JSON-ignored typed collections - attach the
            // synthesized legacy views (after all persistence) so the client's result keeps the automatic
            // feedback after submitting; idempotent when the websocket broadcast already attached them
            programmingFeedbackSynthesizerService.attachSynthesizedFeedback(submittedResult, exercise, false);
            return submittedResult;
        }
        // same for the draft-save response
        programmingFeedbackSynthesizerService.attachSynthesizedFeedback(newManualResult, exercise, false);
        return newManualResult;
    }

    /**
     * Adds the stored automatic feedback rows of the result to the feedback that is saved, since saving replaces the stored feedback. A row the tutor adapted
     * arrives with its id and another type and is taken from the request instead. The rows are added as new instances carrying their ids, because the stored
     * instances are detached and their long feedback text is not loaded.
     *
     * @param newManualResult      the result built from the request
     * @param existingManualResult the stored result with its feedback
     */
    private static void keepStoredAutomaticFeedback(Result newManualResult, Result existingManualResult) {
        Set<Long> incomingFeedbackIds = newManualResult.getFeedbacks().stream().map(Feedback::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        existingManualResult.getFeedbacks().stream().filter(feedback -> feedback.getType() == FeedbackType.AUTOMATIC && !incomingFeedbackIds.contains(feedback.getId()))
                .map(ProgrammingAssessmentService::referenceStoredFeedback).forEach(newManualResult.getFeedbacks()::add);
    }

    private static Feedback referenceStoredFeedback(Feedback storedFeedback) {
        Feedback feedback = new Feedback();
        feedback.setId(storedFeedback.getId());
        feedback.setText(storedFeedback.getText());
        // the stored detail text is the preview of a long feedback text, which is re-attached by its id while saving
        feedback.setDetailText(storedFeedback.getDetailText());
        feedback.setHasLongFeedbackText(storedFeedback.getHasLongFeedbackText());
        feedback.setReference(storedFeedback.getReference());
        feedback.setCredits(storedFeedback.getCredits());
        feedback.setPositive(storedFeedback.isPositive());
        feedback.setType(storedFeedback.getType());
        feedback.setVisibility(storedFeedback.getVisibility());
        feedback.setGradingInstruction(storedFeedback.getGradingInstruction());
        feedback.setTestCase(storedFeedback.getTestCase());
        return feedback;
    }

    private Result submitManualAssessment(Result newManualResult, ProgrammingSubmission submission, StudentParticipation participation, ProgrammingExercise exercise) {
        newManualResult = resultRepository.submitManualAssessment(newManualResult);

        // The assessment as the tutor wrote it, taken before the broadcast below adds the synthesized views of the
        // typed automatic feedback to the same collection. What Athena is sent must not depend on whether the
        // assessment due date has passed.
        final List<Feedback> assessmentFeedback = List.copyOf(newManualResult.getFeedbacks());

        if (participation.getStudent().isPresent()) {
            singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(exercise, participation.getStudent().get(), newManualResult);
        }

        // Note: we always need to report the result over LTI, even if the assessment due date is not over yet.
        // Otherwise, it might never become visible in the external system
        ltiApi.ifPresent(newResultService -> newResultService.onNewResult(participation));
        if (ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            resultWebsocketService.broadcastNewResult(participation, newManualResult);
        }

        sendFeedbackToAthena(exercise, submission, assessmentFeedback);

        return newManualResult;
    }

    /**
     * Send feedback to Athena (if enabled for both the Artemis instance and the exercise).
     */
    private void sendFeedbackToAthena(final ProgrammingExercise exercise, final ProgrammingSubmission programmingSubmission, final Collection<Feedback> feedbacks) {
        courseAthenaConfigRepository.attachToCourseOf(exercise);
        if (athenaFeedbackApi.isPresent() && exercise.areFeedbackSuggestionsEnabled()) {
            athenaFeedbackApi.get().sendFeedback(exercise, programmingSubmission, new ArrayList<>(feedbacks));
        }
    }

}
