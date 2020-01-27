package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.service.TextBlockService.compareByStartIndexReversed;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing TextAssessment.
 */
@RestController
@RequestMapping("/api/text-assessments")
public class TextAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(TextAssessmentResource.class);

    private static final String ENTITY_NAME = "textAssessment";

    private final TextBlockRepository textBlockRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ResultService resultService;

    private final TextAssessmentService textAssessmentService;

    private final TextBlockService textBlockService;

    private final TextExerciseService textExerciseService;

    private final TextSubmissionService textSubmissionService;

    private final TextSubmissionRepository textSubmissionRepository;

    private final WebsocketMessagingService messagingService;

    private final Optional<AutomaticTextFeedbackService> automaticTextFeedbackService;

    public TextAssessmentResource(AuthorizationCheckService authCheckService, ResultService resultService, TextAssessmentService textAssessmentService,
            TextBlockService textBlockService, TextBlockRepository textBlockRepository, TextExerciseService textExerciseService, TextSubmissionRepository textSubmissionRepository,
            UserService userService, TextSubmissionService textSubmissionService, WebsocketMessagingService messagingService, ExerciseService exerciseService,
            Optional<AutomaticTextFeedbackService> automaticTextFeedbackService, ResultRepository resultRepository) {
        super(authCheckService, userService, exerciseService, textSubmissionService, textAssessmentService, resultRepository);

        this.resultService = resultService;
        this.textAssessmentService = textAssessmentService;
        this.textBlockService = textBlockService;
        this.textBlockRepository = textBlockRepository;
        this.textExerciseService = textExerciseService;
        this.textSubmissionRepository = textSubmissionRepository;
        this.textSubmissionService = textSubmissionService;
        this.messagingService = messagingService;
        this.automaticTextFeedbackService = automaticTextFeedbackService;
    }

    /**
     * Saves a given manual textAssessment
     *
     * @param exerciseId the exerciseId of the exercise which will be saved
     * @param resultId the resultId the assessment belongs to
     * @param textAssessments the assessments
     * @return 200 Ok if successful with the corresponding result as body, but sensitive information are filtered out
     */
    @PutMapping("/exercise/{exerciseId}/result/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> saveTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<Feedback> textAssessments) {
        User user = userService.getUserWithGroupsAndAuthorities();
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise, user);

        final var isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(textExercise);
        if (!isAllowedToOverrideExistingResult(resultId, textExercise, user, isAtLeastInstructor)) {
            return forbidden("assessment", "assessmentSaveNotAllowed", "The user is not allowed to override the assessment");
        }

        Result result = textAssessmentService.saveAssessment(resultId, textAssessments, textExercise);

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
            ((StudentParticipation) result.getParticipation()).filterSensitiveInformation();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Submits manual textAssessments for a given result and notify the user if it's before the Assessment Due Date
     *
     * @param exerciseId the exerciseId of the exercise which will be saved
     * @param resultId the resultId the assessment belongs to
     * @param textAssessments the assessments which should be submitted
     * @return 200 Ok if successful with the corresponding result as a body, but sensitive information are filtered out
     */
    @PutMapping("/exercise/{exerciseId}/result/{resultId}/submit")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> submitTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<Feedback> textAssessments) {
        User user = userService.getUserWithGroupsAndAuthorities();
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise, user);

        final var isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(textExercise);
        if (!isAllowedToOverrideExistingResult(resultId, textExercise, user, isAtLeastInstructor)) {
            return forbidden("assessment", "assessmentSaveNotAllowed", "The user is not allowed to override the assessment");
        }

        Result result = textAssessmentService.submitAssessment(resultId, textExercise, textAssessments);
        StudentParticipation studentParticipation = (StudentParticipation) result.getParticipation();
        if (studentParticipation.getExercise().getAssessmentDueDate() == null || studentParticipation.getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now())) {
            // TODO: we should send a result object here that includes the feedback (this might already be the case)
            messagingService.broadcastNewResult(studentParticipation, result);
        }

        if (!authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
            studentParticipation.filterSensitiveInformation();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the assessment update containing the new feedback items and the response to the complaint
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/text-submissions/{submissionId}/assessment-after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateTextAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody AssessmentUpdate assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userService.getUserWithGroupsAndAuthorities();
        TextSubmission textSubmission = textSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        StudentParticipation studentParticipation = (StudentParticipation) textSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkAuthorization(textExercise, user);
        Result result = textAssessmentService.updateAssessmentAfterComplaint(textSubmission.getResult(), textExercise, assessmentUpdate);

        // TODO: in case of automatic assessment, we might want to update the assessment engine

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation && !authCheckService.isAtLeastInstructorForExercise(textExercise)) {
            ((StudentParticipation) result.getParticipation()).setStudent(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @param exerciseId the exerciseId of the exercise for which the assessment gets canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PutMapping("/exercise/{exerciseId}/submission/{submissionId}/cancel-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        return super.cancelAssessment(submissionId);
    }

    /**
     * Splits the TextSubmission corresponding to a resultId into TextBlocks.
     * The TextBlocks get a suggested feedback if automatic assessment is enabled and feedback available
     *
     * @param resultId the resultId the which needs TextBlocks
     * @return 200 Ok if successful with the result, belonging to the TextBlocks as body, but sensitive information are filtered out
     * @throws EntityNotFoundException if the corresponding Exercise isn't a TextExercise
     * @throws AccessForbiddenException if current user is not at least teaching assistant in the given exercise
     */
    @GetMapping("/result/{resultId}/with-textblocks")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getResultWithPredefinedTextblocks(@PathVariable Long resultId) throws EntityNotFoundException, AccessForbiddenException {
        User user = userService.getUserWithGroupsAndAuthorities();
        final Result result = resultService.findOneWithEagerSubmissionAndFeedback(resultId);
        final StudentParticipation studentParticipation = (StudentParticipation) result.getParticipation();
        final Exercise exercise = studentParticipation.getExercise();
        checkAuthorization(exercise, user);

        if (!(exercise instanceof TextExercise)) {
            throw new BadRequestAlertException("No text exercise found for the given ID.", "textExercise", "exerciseNotFound");
        }

        final TextExercise textExercise = (TextExercise) exercise;

        computeBlocks(result, textExercise);

        TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final List<TextBlock> textBlocks = textBlockRepository.findAllBySubmissionId(textSubmission.getId());
        textBlocks.sort(compareByStartIndexReversed);
        textSubmission.setBlocks(textBlocks);

        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user) && result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation) {
            ((StudentParticipation) result.getParticipation()).filterSensitiveInformation();
        }

        return ResponseEntity.ok(result);
    }

    private void computeBlocks(Result result, TextExercise textExercise) {
        if (automaticTextFeedbackService.isPresent() && textExercise.isAutomaticAssessmentEnabled()) {
            automaticTextFeedbackService.get().suggestFeedback(result);
        }
        else {
            textBlockService.prepopulateFeedbackBlocks(result);
        }
    }

    /**
     * Given an exerciseId and a submissionId, the method retrieves from the database all the data needed by the tutor to assess the submission. If the tutor has already started
     * assessing the submission, then we also return all the results the tutor has already inserted. If another tutor has already started working on this submission, the system
     * returns an error
     *
     * @param submissionId the id of the submission we want
     * @return a Participation of the tutor in the submission
     */
    @GetMapping("/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> retrieveParticipationForSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get data for tutors text assessment submission: {}", submissionId);

        final Optional<TextSubmission> optionalTextSubmission = textSubmissionRepository.findByIdWithEagerParticipationExerciseResultAssessorAndBlocks(submissionId);
        if (optionalTextSubmission.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID."))
                    .body(null);
        }

        final TextSubmission textSubmission = optionalTextSubmission.get();
        final User user = userService.getUserWithGroupsAndAuthorities();
        final Participation participation = textSubmission.getParticipation();
        final TextExercise exercise = (TextExercise) participation.getExercise();
        checkAuthorization(exercise, user);
        final boolean isAtLeastInstructorForExercise = authCheckService.isAtLeastInstructorForExercise(exercise, user);

        Result result = textSubmission.getResult();
        if (result != null) {
            final User assessor = result.getAssessor();
            if (!isAtLeastInstructorForExercise && assessor != null && !assessor.getLogin().equals(user.getLogin()) && result.getCompletionDate() == null) {
                throw new BadRequestAlertException("This submission is being assessed by another tutor", ENTITY_NAME, "alreadyAssessed");
            }

        }
        else {
            result = new Result();
            result.setParticipation(participation);
            result.setSubmission(textSubmission);
            resultService.createNewRatedManualResult(result, false);
        }

        // Set Submissions and Results of Participation to include requested only
        participation.setSubmissions(Set.of(textSubmission));
        participation.setResults(Set.of(result));

        // Remove Result from Submission, as it is send in participation.results[0]
        textSubmission.setResult(null);

        final List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
        result.setFeedbacks(assessments);

        if (exercise.isAutomaticAssessmentEnabled() && (textSubmission.getBlocks() == null || textSubmission.getBlocks().isEmpty())) {
            computeBlocks(result, exercise);

            textSubmission.getBlocks().sort(compareByStartIndexReversed);
        }

        if (!isAtLeastInstructorForExercise && participation instanceof StudentParticipation) {
            final StudentParticipation studentParticipation = (StudentParticipation) participation;
            studentParticipation.filterSensitiveInformation();
        }

        return ResponseEntity.ok(participation);
    }

    /**
     * Retrieve the result of an example assessment, only if the user is an instructor or if the submission is used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission which must be connected to an example submission
     * @return the example result linked to the submission
     */
    // TODO: we should move this method up because it is independent of the exercise type
    @GetMapping("/exercise/{exerciseId}/submission/{submissionId}/example-result")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getExampleResultForTutor(@PathVariable long exerciseId, @PathVariable long submissionId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        final var textExercise = textExerciseService.findOne(exerciseId);

        // If the user is not at least a tutor for this exercise, return error
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
            return forbidden();
        }
        Submission submission = textAssessmentService.getSubmissionOfExampleSubmissionWithResult(submissionId);

        // If the user is not an instructor, and this is not an example submission used for tutorial, do not provide the results
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(textExercise, user);
        if (!submission.isExampleSubmission() && !isAtLeastInstructor) {
            return forbidden();
        }
        return ResponseEntity.ok(submission.getResult());
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }

    /**
     * Checks if the given textExercise is valid and if the requester have the
     * required permissions
     * @param textExercise which needs to be checked
     * @throws BadRequestAlertException if no request was found
     *
     */
    private void checkTextExerciseForRequest(@Nullable TextExercise textExercise, User user) {
        if (textExercise == null) {
            throw new BadRequestAlertException("No exercise was found for the given ID.", "textExercise", "exerciseNotFound");
        }

        validateExercise(textExercise);
        checkAuthorization(textExercise, user);
    }
}
