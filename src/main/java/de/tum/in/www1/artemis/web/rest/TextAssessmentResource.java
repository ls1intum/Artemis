package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ResultRepository;
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

    private final ParticipationService participationService;

    private final ResultService resultService;

    private final TextAssessmentService textAssessmentService;

    private final TextBlockService textBlockService;

    private final TextExerciseService textExerciseService;

    private final TextSubmissionService textSubmissionService;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ResultRepository resultRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public TextAssessmentResource(AuthorizationCheckService authCheckService, ParticipationService participationService, ResultService resultService,
            TextAssessmentService textAssessmentService, TextBlockService textBlockService, TextExerciseService textExerciseService,
            TextSubmissionRepository textSubmissionRepository, ResultRepository resultRepository, UserService userService, TextSubmissionService textSubmissionService,
            SimpMessageSendingOperations messagingTemplate) {
        super(authCheckService, userService);

        this.participationService = participationService;
        this.resultService = resultService;
        this.textAssessmentService = textAssessmentService;
        this.textBlockService = textBlockService;
        this.textExerciseService = textExerciseService;
        this.textSubmissionRepository = textSubmissionRepository;
        this.resultRepository = resultRepository;
        this.textSubmissionService = textSubmissionService;
        this.messagingTemplate = messagingTemplate;
    }

    @PutMapping("/exercise/{exerciseId}/result/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO: we should send a result object here that includes the feedback
    public ResponseEntity<Result> saveTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<Feedback> textAssessments) {
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise);

        Result result = textAssessmentService.saveAssessment(resultId, textAssessments, textExercise);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/exercise/{exerciseId}/result/{resultId}/submit")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO: we should send a result object here that includes the feedback
    public ResponseEntity<Result> submitTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<Feedback> textAssessments) {
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise);

        Result result = textAssessmentService.submitAssessment(resultId, textExercise, textAssessments);
        if (result.getParticipation().getExercise().getAssessmentDueDate() == null
                || result.getParticipation().getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now())) {
            messagingTemplate.convertAndSend("/topic/participation/" + result.getParticipation().getId() + "/newResults", result);
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/exercise/{exerciseId}/result/{resultId}/after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateTextAssessmentAfterComplaint(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody AssessmentUpdate assessmentUpdate) {
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise);
        Result originalResult = resultService.findOneWithEagerFeedbacks(resultId);
        Result result = textAssessmentService.updateAssessmentAfterComplaint(originalResult, textExercise, assessmentUpdate);
        return ResponseEntity.ok(result);
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PutMapping("/exercise/{exerciseId}/submission/{submissionId}/cancel-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity cancelAssessment(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        log.debug("REST request to cancel assessment of submission: {}", submissionId);
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise);
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

    @Transactional
    @GetMapping("/result/{resultId}/with-textblocks")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getResultWithPredefinedTextblocks(@PathVariable Long resultId) throws EntityNotFoundException, AccessForbiddenException {
        final Result result = resultService.findOneWithSubmission(resultId);
        final Exercise exercise = result.getParticipation().getExercise();
        checkAuthorization(exercise);

        textBlockService.prepopulateFeedbackBlocks(result);
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
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        checkTextExerciseForRequest(textExercise);

        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(submissionId);
        if (!textSubmission.isPresent()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID."))
                    .body(null);
        }

        Participation participation = textSubmission.get().getParticipation();
        participation = participationService.findOneWithEagerResultsAndSubmissions(participation.getId());

        if (!participation.getResults().isEmpty()) {
            User user = userService.getUser();

            // TODO: this does not work if we have multiple submissions / results for the same participation
            // this happens some and then, I guess because students press the save/submit button simultaneously multiple times, we actually have about 100 cases in the database
            if (participation.findLatestSubmission().isPresent()) {
                Result latestResult = participation.findLatestSubmission().get().getResult();
                User assessor = latestResult.getAssessor();

                if (authCheckService.isAtLeastInstructorForExercise(textExercise)) {
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
            resultService.createNewResult(result, false);
            participation.addResult(result);
        }

        for (Result result : participation.getResults()) {
            List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
            result.setFeedbacks(assessments);
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
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(submissionId);
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        if (!textSubmission.isPresent()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID."))
                    .body(null);
        }

        // If the user is not an instructor, and this is not an example submission used for tutorial,
        // do not provide the results
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(textExercise);
        if (!textSubmission.get().isExampleSubmission() && !isAtLeastInstructor) {
            return forbidden();
        }

        // If the user is not at least a tutor for this exercise, return error
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(textExercise)) {
            return forbidden();
        }

        Optional<Result> databaseResult = this.resultRepository.findDistinctBySubmissionId(submissionId);
        Result result = databaseResult.orElseGet(() -> {
            Result newResult = new Result();
            newResult.setSubmission(textSubmission.get());
            newResult.setExampleResult(true);
            resultService.createNewResult(newResult, false);
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

    @Nullable
    private void checkTextExerciseForRequest(TextExercise textExercise) {
        if (textExercise == null) {
            throw new BadRequestAlertException("No exercise was found for the given ID.", "textExercise", "exerciseNotFound");
        }

        validateExercise(textExercise);
        checkAuthorization(textExercise);
    }
}
