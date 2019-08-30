package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
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

    private final ParticipationService participationService;

    private final ResultService resultService;

    private final TextAssessmentService textAssessmentService;

    private final TextBlockService textBlockService;

    private final TextExerciseService textExerciseService;

    private final TextSubmissionService textSubmissionService;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ResultRepository resultRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final Optional<AutomaticTextFeedbackService> automaticTextFeedbackService;

    public TextAssessmentResource(AuthorizationCheckService authCheckService, ParticipationService participationService, ResultService resultService,
            TextAssessmentService textAssessmentService, TextBlockService textBlockService, TextBlockRepository textBlockRepository, TextExerciseService textExerciseService,
            TextSubmissionRepository textSubmissionRepository, ResultRepository resultRepository, UserService userService, TextSubmissionService textSubmissionService,
            SimpMessageSendingOperations messagingTemplate, Optional<AutomaticTextFeedbackService> automaticTextFeedbackService) {
        super(authCheckService, userService);

        this.participationService = participationService;
        this.resultService = resultService;
        this.textAssessmentService = textAssessmentService;
        this.textBlockService = textBlockService;
        this.textBlockRepository = textBlockRepository;
        this.textExerciseService = textExerciseService;
        this.textSubmissionRepository = textSubmissionRepository;
        this.resultRepository = resultRepository;
        this.textSubmissionService = textSubmissionService;
        this.messagingTemplate = messagingTemplate;
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
    // TODO: we should send a result object here that includes the feedback
    public ResponseEntity<Result> saveTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<Feedback> textAssessments) {
        User user = userService.getUserWithGroupsAndAuthorities();
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise, user);

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
    // TODO: we should send a result object here that includes the feedback
    public ResponseEntity<Result> submitTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<Feedback> textAssessments) {
        User user = userService.getUserWithGroupsAndAuthorities();
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise, user);

        Result result = textAssessmentService.submitAssessment(resultId, textExercise, textAssessments);
        if (result.getParticipation().getExercise().getAssessmentDueDate() == null
                || result.getParticipation().getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now())) {
            messagingTemplate.convertAndSend("/topic/participation/" + result.getParticipation().getId() + "/newResults", result);
        }

        if (!authCheckService.isAtLeastInstructorForExercise(textExercise, user) && result.getParticipation() != null
                && result.getParticipation() instanceof StudentParticipation) {
            ((StudentParticipation) result.getParticipation()).filterSensitiveInformation();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Updates a Assessment to a TextExercise if the student complaints
     *
     * @param exerciseId the exerciseId of the exercise which will be corrected
     * @param resultId the resultId the assessment belongs to
     * @param assessmentUpdate the update of the Assessment
     * @return 200 Ok if successful with the updated result as a body, but sensitive information are filtered out
     */
    @PutMapping("/exercise/{exerciseId}/result/{resultId}/after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateTextAssessmentAfterComplaint(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody AssessmentUpdate assessmentUpdate) {
        User user = userService.getUserWithGroupsAndAuthorities();
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise, user);
        Result originalResult = resultService.findOneWithEagerFeedbacks(resultId);
        Result result = textAssessmentService.updateAssessmentAfterComplaint(originalResult, textExercise, assessmentUpdate);

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
            ((StudentParticipation) result.getParticipation()).filterSensitiveInformation();
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
    public ResponseEntity cancelAssessment(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        log.debug("REST request to cancel assessment of submission: {}", submissionId);
        User user = userService.getUserWithGroupsAndAuthorities();
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise, user);
        TextSubmission submission = textSubmissionService.findOneWithEagerResultAndAssessor(submissionId);
        if (submission.getResult() == null) {
            // if there is no result everything is fine
            return ResponseEntity.ok().build();
        }
        if (!userService.getUser().getId().equals(submission.getResult().getAssessor().getId())) {
            // you cannot cancel the assessment of other tutors
            return forbidden();
        }
        textAssessmentService.cancelAssessmentOfSubmission(submission);
        return ResponseEntity.ok().build();
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
        final Exercise exercise = result.getParticipation().getExercise();
        checkAuthorization(exercise, user);

        if (!(exercise instanceof TextExercise)) {
            throw new BadRequestAlertException("No text exercise found for the given ID.", "textExercise", "exerciseNotFound");
        }

        final TextExercise textExercise = (TextExercise) exercise;

        if (automaticTextFeedbackService.isPresent() && textExercise.isAutomaticAssessmentEnabled()) {
            automaticTextFeedbackService.get().suggestFeedback(result);
        }
        else {
            textBlockService.prepopulateFeedbackBlocks(result);
        }

        Comparator<TextBlock> byStartIndexReversed = (TextBlock first, TextBlock second) -> Integer.compare(second.getStartIndex(), first.getStartIndex());
        TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        textSubmission.getBlocks().sort(byStartIndexReversed);

        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user) && result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation) {
            ((StudentParticipation) result.getParticipation()).filterSensitiveInformation();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Given an exerciseId and a submissionId, the method retrieves from the database all the data needed by the tutor to assess the submission. If the tutor has already started
     * assessing the submission, then we also return all the results the tutor has already inserted. If another tutor has already started working on this submission, the system
     * returns an error
     *
     * @param exerciseId   the id of the exercise we want the submission
     * @param submissionId the id of the submission we want
     * @return a Participation of the tutor in the submission
     */
    @GetMapping("/exercise/{exerciseId}/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> retrieveParticipationForSubmission(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        log.debug("REST request to get data for tutors text assessment exercise: {}, submission: {}", exerciseId, submissionId);
        User user = userService.getUserWithGroupsAndAuthorities();
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise, user);

        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(submissionId);
        if (textSubmission.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID."))
                    .body(null);
        }

        Participation participation = textSubmission.get().getParticipation();
        participation = participationService.findOneWithEagerResultsAndSubmissionsAndAssessor(participation.getId());
        List<TextBlock> textBlocks = textBlockRepository.findAllBySubmissionId(submissionId);

        if (!participation.getResults().isEmpty()) {

            // TODO: this does not work if we have multiple submissions / results for the same participation
            // this happens some and then, I guess because students press the save/submit button simultaneously multiple times, we actually have about 100 cases in the database
            if (participation.findLatestSubmission().isPresent()) {
                Result latestResult = participation.findLatestSubmission().get().getResult();
                User assessor = latestResult.getAssessor();

                if (authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
                    // skip this case as, because instructors are allowed to override assessments
                }
                // Another tutor started assessing this submission and hasn't finished yet
                else if (assessor != null && !assessor.getLogin().equals(user.getLogin()) && latestResult.getCompletionDate() == null) {
                    throw new BadRequestAlertException("This submission is being assessed by another tutor", ENTITY_NAME, "alreadyAssessed");
                }
            }
        }

        if (participation.getResults().isEmpty()) {
            Result result = new Result();
            result.setParticipation(participation);
            result.setSubmission(textSubmission.get());
            resultService.createNewManualResult(result, false);
            participation.addResult(result);
        }

        for (Result result : participation.getResults()) {
            List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
            result.setFeedbacks(assessments);
            ((TextSubmission) result.getSubmission()).setBlocks(textBlocks);
        }

        if (!authCheckService.isAtLeastInstructorForExercise(textExercise, user) && participation instanceof StudentParticipation) {
            ((StudentParticipation) participation).filterSensitiveInformation();
        }

        return ResponseEntity.ok(participation);
    }

    /**
     * Retrieve the result of an example assessment, only if the user is an instructor or if the submission is used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission
     * @return the result linked to the submission
     */
    @GetMapping("/exercise/{exerciseId}/submission/{submissionId}/exampleAssessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getExampleAssessmentForTutor(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(submissionId);
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        if (textSubmission.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID."))
                    .body(null);
        }

        // If the user is not an instructor, and this is not an example submission used for tutorial,
        // do not provide the results
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(textExercise, user);
        if (!textSubmission.get().isExampleSubmission() && !isAtLeastInstructor) {
            return forbidden();
        }

        // If the user is not at least a tutor for this exercise, return error
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
            return forbidden();
        }

        Optional<Result> databaseResult = this.resultRepository.findDistinctBySubmissionId(submissionId);
        Result result = databaseResult.orElseGet(() -> {
            Result newResult = new Result();
            newResult.setSubmission(textSubmission.get());
            newResult.setExampleResult(true);
            resultService.createNewManualResult(newResult, false);
            return newResult;
        });

        List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
        result.setFeedbacks(assessments);

        return ResponseEntity.ok(result);
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
