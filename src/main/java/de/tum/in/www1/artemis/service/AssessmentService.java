package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.programming.ProgrammingAssessmentService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class AssessmentService {

    private final ComplaintResponseService complaintResponseService;

    private final ComplaintRepository complaintRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final ResultRepository resultRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    protected final ResultService resultService;

    private final ExamDateService examDateService;

    private final ExerciseDateService exerciseDateService;

    protected final SubmissionRepository submissionRepository;

    protected final GradingCriterionRepository gradingCriterionRepository;

    protected final UserRepository userRepository;

    private final SubmissionService submissionService;

    private final LtiService ltiService;

    public AssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService, SubmissionService submissionService,
            SubmissionRepository submissionRepository, ExamDateService examDateService, ExerciseDateService exerciseDateService,
            GradingCriterionRepository gradingCriterionRepository, UserRepository userRepository, LtiService ltiService) {
        this.complaintResponseService = complaintResponseService;
        this.complaintRepository = complaintRepository;
        this.feedbackRepository = feedbackRepository;
        this.resultRepository = resultRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultService = resultService;
        this.submissionService = submissionService;
        this.submissionRepository = submissionRepository;
        this.examDateService = examDateService;
        this.exerciseDateService = exerciseDateService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.userRepository = userRepository;
        this.ltiService = ltiService;
    }

    /**
     * Handles an assessment update after a complaint. It first saves the corresponding complaint response and then updates the Result that was complaint about. Note, that it
     * updates the score and the feedback of the original Result, but NOT the assessor. The user that is responsible for the update can be found in the 'reviewer' field of the
     * complaint. The original Result gets stored in the 'resultBeforeComplaint' field of the ComplaintResponse for future lookup.
     *
     * @param originalResult   the original assessment that was complained about
     * @param exercise         the exercise to which the result belongs
     * @param assessmentUpdate the assessment update containing a ComplaintResponse and the updated Feedback list
     * @return the updated Result
     */
    public Result updateAssessmentAfterComplaint(Result originalResult, Exercise exercise, AssessmentUpdate assessmentUpdate) {
        if (assessmentUpdate.getFeedbacks() == null || assessmentUpdate.getComplaintResponse() == null) {
            throw new BadRequestAlertException("Feedbacks and complaint response must not be null.", "AssessmentUpdate", "notnull");
        }
        // Save the complaint response
        ComplaintResponse complaintResponse = complaintResponseService.resolveComplaint(assessmentUpdate.getComplaintResponse());

        // Create a new result which is a copy of the original result.
        Result newResult = submissionService.createResultAfterComplaintResponse(originalResult.getSubmission(), originalResult, assessmentUpdate.getFeedbacks());
        newResult.setAssessor(complaintResponse.getReviewer());
        newResult.setAssessmentType(originalResult.getAssessmentType());

        resultRepository.save(newResult);

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            newResult.calculateScoreForProgrammingExercise(programmingExercise);
            newResult.setCompletionDate(ZonedDateTime.now());
            newResult.setHasFeedback(true);
            newResult.setRated(true);
            newResult.copyProgrammingExerciseCounters(originalResult);

            Result savedResult = resultRepository.save(newResult);
            return resultRepository.findByIdWithEagerAssessor(savedResult.getId()).orElseThrow(); // to eagerly load assessor
        }
        else {
            return resultRepository.submitResult(newResult, exercise, exerciseDateService.getDueDate(newResult.getParticipation()));
        }
    }

    /**
     * Checks if the user can create or override a submitted result.
     * If the result does not yet exist or is not yet submitted, this tutors can create or override results.
     * Overriding is only possible if the same tutor overrides before the assessment due date.
     * Instructors can always create and override results also after the assessment due date.
     *
     * @param existingResult the existing result in case the result is updated (submitted or overridden)
     * @param exercise the exercise to which the submission and result belong and which potentially includes an assessment due date
     * @param user the user who initiates a request
     * @param isAtLeastInstructor whether the given user is an instructor for the given exercise
     * @param participation the participation to which the submission and result belong to
     * @return true if the given user can override a potentially existing result
     */
    public boolean isAllowedToCreateOrOverrideResult(Result existingResult, Exercise exercise, StudentParticipation participation, User user, boolean isAtLeastInstructor) {

        final boolean isExamMode = exercise.isExamExercise();
        ZonedDateTime assessmentDueDate;

        // For exam exercises, tutors cannot override submissions when the publishing result date is in the past (assessmentDueDate)
        if (isExamMode) {
            assessmentDueDate = exercise.getExerciseGroup().getExam().getPublishResultsDate();
        }
        else {
            assessmentDueDate = exercise.getAssessmentDueDate();
        }

        final boolean isAllowedToBeAssessor = isAllowedToBeAssessorOfResult(existingResult, exercise, participation, user);
        // TODO make sure that tutors cannot assess the first assessment after the assessmentDueDate/publish result date (post). This is currently just used in the put request.
        // Check if no result is available (first assessment)
        if (existingResult == null) {
            // Tutors can assess exam exercises only after the last student has finished the exam and before the publishing result date
            if (isExamMode && !isAtLeastInstructor) {
                final Exam exam = exercise.getExerciseGroup().getExam();
                ZonedDateTime latestExamDueDate = examDateService.getLatestIndividualExamEndDate(exam.getId());
                if (latestExamDueDate.isAfter(ZonedDateTime.now()) || (exam.getPublishResultsDate() != null && exam.getPublishResultsDate().isBefore(ZonedDateTime.now()))) {
                    return false;
                }
            }
            return isAllowedToBeAssessor || isAtLeastInstructor;
        }

        // If the result exists, but was not yet submitted (i.e. completionDate not set), the tutor and the instructor can override, independent of the assessment due date
        if (existingResult.getCompletionDate() == null) {
            return isAllowedToBeAssessor || isAtLeastInstructor;
        }

        // If the result was already submitted, the tutor can only override before a potentially existing assessment due date
        final boolean isBeforeAssessmentDueDate = assessmentDueDate == null || ZonedDateTime.now().isBefore(assessmentDueDate);
        return (isAllowedToBeAssessor && isBeforeAssessmentDueDate) || isAtLeastInstructor;
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submission the submission for which the current assessment should be canceled
     */
    @Transactional // NOTE: As we use delete methods with underscores, we need a transactional context here!
    public void cancelAssessmentOfSubmission(Submission submission) {
        StudentParticipation participation = studentParticipationRepository.findWithEagerResultsById(submission.getParticipation().getId())
                .orElseThrow(() -> new BadRequestAlertException("Participation could not be found", "participation", "notfound"));
        // cancel is only possible for the latest result.
        Result result = submission.getLatestResult();

        // We only want to be able to cancel a result if it is not of the AUTOMATIC AssessmentType
        if (result != null && result.getAssessmentType() != null && result.getAssessmentType() != AssessmentType.AUTOMATIC) {
            participation.removeResult(result);
            feedbackRepository.deleteByResult_Id(result.getId());
            resultService.deleteResult(result.getId());
        }
    }

    /**
     * Returns whether a user is allowed to be the assessor of an existing result
     * @param result Result for which to check if the user can be the assessor
     * @param exercise Exercise to which the result belongs to
     * @param user User for whom to check if they can be the assessor of the given result
     * @return true if the user is allowed to be the assessor, false otherwise
     */
    private boolean isAllowedToBeAssessorOfResult(Result result, Exercise exercise, StudentParticipation participation, User user) {
        if (exercise.isTeamMode()) {
            // for team exercises only the team tutor is allowed to be the assessor
            return participation.getTeam().orElseThrow().isOwner(user);
        }
        else if (result != null) {
            // for individual exercises a tutor can be the assessor if they already are the assessor or if there is no assessor yet
            return result.getAssessor() == null || user.equals(result.getAssessor());
        }
        else {
            return true;
        }
    }

    /**
     * Gets an example submission with the given submissionId and returns the result of the submission.
     *
     * @param submissionId the id of the example modeling submission
     * @return the result of the submission
     * @throws EntityNotFoundException when no submission can be found for the given id
     */
    public Result getExampleAssessment(long submissionId) {
        Optional<Submission> optionalSubmission = submissionRepository.findExampleSubmissionByIdWithEagerResult(submissionId);
        Submission submission = optionalSubmission.orElseThrow(() -> new EntityNotFoundException("Example Submission with id \"" + submissionId + "\" does not exist"));
        return submission.getLatestResult();
    }

    /**
     * This function is used for submitting a manual assessment/result. It gets the result that belongs to the given resultId, updates the completion date, sets the assessment type
     * to MANUAL and sets the assessor attribute. Afterwards, it saves the update result in the database again.
     *
     * For programming exercises we use a different approach see {@link ResultRepository#submitManualAssessment(long)})}
     *
     * @param resultId the id of the result that should be submitted
     * @param exercise the exercise the assessment belongs to
     * @param submissionDate the date manual assessment was submitted
     * @return the ResponseEntity with result as body
     */
    public Result submitManualAssessment(long resultId, Exercise exercise, ZonedDateTime submissionDate) {
        Result result = resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("No result for the given resultId could be found"));
        result.setRatedIfNotExceeded(exerciseDateService.getDueDate(result.getParticipation()).orElse(null), submissionDate);
        result.setCompletionDate(ZonedDateTime.now());
        result = resultRepository.submitResult(result, exercise, exerciseDateService.getDueDate(result.getParticipation()));
        // Note: we always need to report the result (independent of the assessment due date) over LTI, otherwise it might never become visible in the external system
        ltiService.onNewResult((StudentParticipation) result.getParticipation());
        return result;
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the
     * database. If a result with the given id exists, it will be overridden. if not, a new result will be created.
     *
     * For programming exercises we use a different approach see {@link ProgrammingAssessmentService#saveManualAssessment(Result)}
     *
     * @param submission the file upload submission to which the feedback belongs to
     * @param feedbackList the assessment as a feedback list that should be added to the result of the corresponding submission
     * @param resultId resultId of the submission we what to save the @feedbackList to, null if no result exists
     * @return result that was saved in the database
     */
    public Result saveManualAssessment(final Submission submission, final List<Feedback> feedbackList, Long resultId) {
        Result result = submission.getResults().stream().filter(res -> res.getId().equals(resultId)).findAny().orElse(null);

        if (result == null) {
            result = submissionService.saveNewEmptyResult(submission);
        }

        // important to not lose complaint information when overriding an assessment
        if (result.getHasComplaint().isEmpty()) {
            result.setHasComplaint(false);
        }

        result.setExampleResult(submission.isExampleSubmission());
        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userRepository.getUser();
        result.setAssessor(user);

        result.updateAllFeedbackItems(feedbackList, false);
        // Note: this boolean flag is only used for programming exercises
        result.setHasFeedback(false);
        result.determineAssessmentType();

        if (result.getSubmission() == null) {
            result.setSubmission(submission);
            submission.addResult(result);
            submissionRepository.save(submission);
        }
        // Workaround to prevent the assessor turning into a proxy object after saving
        var assessor = result.getAssessor();
        result = resultRepository.save(result);
        result.setAssessor(assessor);
        return result;
    }

    /**
     * Deletes the result of a submission.
     *
     * @param submission - the submission which the result belongs to
     * @param result     - the result that should get deleted
     */
    @Transactional // NOTE: As we use delete methods with underscores, we need a transactional context here!
    public void deleteAssessment(Submission submission, Result result) {

        if (complaintRepository.findByResultId(result.getId()).isPresent()) {
            throw new BadRequestAlertException("Result has a complaint", "result", "hasComplaint");
        }
        submission.getResults().remove(result);
        feedbackRepository.deleteByResult_Id(result.getId());
        resultService.deleteResult(result.getId());
        // this keeps the result order intact
        submissionRepository.save(submission);
    }
}
