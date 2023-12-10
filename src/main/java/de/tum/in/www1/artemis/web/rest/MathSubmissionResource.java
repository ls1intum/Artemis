package de.tum.in.www1.artemis.web.rest;

import java.util.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.MathSubmissionService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing MathSubmission.
 */
@RestController
@RequestMapping("/api")
public class MathSubmissionResource extends AbstractSubmissionResource {

    private static final String ENTITY_NAME = "mathSubmission";

    private final Logger log = LoggerFactory.getLogger(MathSubmissionResource.class);

    private final MathSubmissionRepository mathSubmissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final MathExerciseRepository mathExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final MathSubmissionService mathSubmissionService;

    private final UserRepository userRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ExamSubmissionService examSubmissionService;

    private final PlagiarismService plagiarismService;

    public MathSubmissionResource(SubmissionRepository submissionRepository, ResultService resultService, MathSubmissionRepository mathSubmissionRepository,
            ExerciseRepository exerciseRepository, MathExerciseRepository mathExerciseRepository, AuthorizationCheckService authCheckService,
            MathSubmissionService mathSubmissionService, UserRepository userRepository, StudentParticipationRepository studentParticipationRepository,
            GradingCriterionRepository gradingCriterionRepository, ExamSubmissionService examSubmissionService, PlagiarismService plagiarismService) {
        super(submissionRepository, resultService, authCheckService, userRepository, exerciseRepository, mathSubmissionService, studentParticipationRepository);
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.mathExerciseRepository = mathExerciseRepository;
        this.authCheckService = authCheckService;
        this.mathSubmissionService = mathSubmissionService;
        this.userRepository = userRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.examSubmissionService = examSubmissionService;
        this.plagiarismService = plagiarismService;
    }

    /**
     * POST /exercises/{exerciseId}/math-submissions : Create a new mathSubmission. This is called when a student saves his/her answer
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param mathSubmission the mathSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmission> createMathSubmission(@PathVariable Long exerciseId, @Valid @RequestBody MathSubmission mathSubmission) {
        log.debug("REST request to save math submission : {}", mathSubmission);
        if (mathSubmission.getId() != null) {
            throw new BadRequestAlertException("A new math submission cannot already have an ID", ENTITY_NAME, "idExists");
        }
        return handleMathSubmission(exerciseId, mathSubmission);
    }

    /**
     * PUT /exercises/{exerciseId}/math-submissions : Updates an existing math submission or creates a new one.
     * The submit specific handling occurs in the MathSubmissionService.createMathSubmission() and save() methods.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param mathSubmission the mathSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated mathSubmission, or with status 400 (Bad Request) if the mathSubmission is not valid, or with status
     *         500 (Internal Server Error) if the mathSubmission couldn't be updated
     */
    @PutMapping("/exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmission> updateMathSubmission(@PathVariable long exerciseId, @Valid @RequestBody MathSubmission mathSubmission) {
        log.debug("REST request to update math submission: {}", mathSubmission);
        if (mathSubmission.getId() == null) {
            return createMathSubmission(exerciseId, mathSubmission);
        }
        return handleMathSubmission(exerciseId, mathSubmission);
    }

    @NotNull
    private ResponseEntity<MathSubmission> handleMathSubmission(long exerciseId, MathSubmission mathSubmission) {
        long start = System.currentTimeMillis();
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var exercise = mathExerciseRepository.findByIdElseThrow(exerciseId);

        // Apply further checks if it is an exam submission
        examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, user);

        // Prevent multiple submissions (currently only for exam submissions)
        mathSubmission = (MathSubmission) examSubmissionService.preventMultipleSubmissions(exercise, mathSubmission, user);
        // Check if the user is allowed to submit
        mathSubmissionService.checkSubmissionAllowanceElseThrow(exercise, mathSubmission, user);

        mathSubmission = mathSubmissionService.handleMathSubmission(mathSubmission, exercise, user);
        mathSubmissionService.hideDetails(mathSubmission, user);
        long end = System.currentTimeMillis();
        log.info("handleMathSubmission took {}ms for exercise {} and user {}", end - start, exerciseId, user.getLogin());
        return ResponseEntity.ok(mathSubmission);
    }

    /**
     * GET /math-submissions/:id : get the "id" mathSubmission.
     *
     * @param submissionId the id of the mathSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the mathSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/math-submissions/{submissionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmission> getMathSubmissionWithResults(@PathVariable long submissionId) {
        log.debug("REST request to get math submission: {}", submissionId);
        var mathSubmission = mathSubmissionRepository.findWithEagerResultsById(submissionId).orElseThrow(() -> new EntityNotFoundException("MathSubmission", submissionId));

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(mathSubmission.getParticipation().getExercise())) {
            // anonymize and throw exception if not authorized to view submission
            plagiarismService.checkAccessAndAnonymizeSubmissionForStudent(mathSubmission, userRepository.getUser().getLogin());
            return ResponseEntity.ok(mathSubmission);
        }

        return ResponseEntity.ok().body(mathSubmission);
    }

    /**
     * GET /math-submissions : get all the mathSubmissions for an exercise. It is possible to filter, to receive only the one that have been already submitted,
     * or only the one assessed by the tutor who is doing the call.
     * In case of exam exercise, it filters out all test run submissions.
     *
     * @param exerciseId      exerciseID for which all submissions should be returned
     * @param correctionRound get submission with results in the correction round
     * @param submittedOnly   mark if only submitted Submissions should be returned
     * @param assessedByTutor mark if only assessed Submissions should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of mathSubmissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Submission>> getAllMathSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get all MathSubmissions");
        return super.getAllSubmissions(exerciseId, submittedOnly, assessedByTutor, correctionRound);
    }

    /**
     * GET /math-submission-without-assessment : get one mathSubmission without assessment.
     *
     * @param exerciseId                      exerciseID for which a submission should be returned
     * @param correctionRound                 correctionRound for which submissions without a result should be returned
     *                                            TODO: Replace ?head=true with HTTP HEAD request
     * @param skipAssessmentOrderOptimization optional value to define if the assessment queue should be skipped. Use if only checking for needed assessments.
     * @param lockSubmission                  optional value to define if the submission should be locked and has the value of false if not set manually
     * @return the ResponseEntity with status 200 (OK) and the list of mathSubmissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/math-submission-without-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<MathSubmission> getMathSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "head", defaultValue = "false") boolean skipAssessmentOrderOptimization,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get a math submission without assessment");
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        if (!(exercise instanceof MathExercise)) {
            throw new BadRequestAlertException("The exerciseId does not belong to a math exercise", ENTITY_NAME, "wrongExerciseType");
        }

        // Check if tutors can start assessing the students submission
        this.mathSubmissionService.checkIfExerciseDueDateIsReached(exercise);

        // Check if the limit of simultaneously locked submissions has been reached
        mathSubmissionService.checkSubmissionLockLimit(exercise.getCourseViaExerciseGroupOrCourseMember().getId());

        Optional<MathSubmission> optionalMathSubmission = mathSubmissionService.getRandomMathSubmissionEligibleForNewAssessment((MathExercise) exercise,
                skipAssessmentOrderOptimization, exercise.isExamExercise(), correctionRound);

        // No more unassessed submissions
        if (optionalMathSubmission.isEmpty()) {
            return ResponseEntity.ok(null);
        }

        final MathSubmission mathSubmission = optionalMathSubmission.get();

        if (lockSubmission) {
            mathSubmissionService.lockMathSubmissionToBeAssessed(optionalMathSubmission.get(), correctionRound);
            // TODO: math assessment
            // mathAssessmentService.prepareSubmissionForAssessment(mathSubmission, mathSubmission.getResultForCorrectionRound(correctionRound));
        }

        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);

        // Make sure the exercise is connected to the participation in the json response
        final StudentParticipation studentParticipation = (StudentParticipation) mathSubmission.getParticipation();
        studentParticipation.setExercise(exercise);
        mathSubmission.getParticipation().getExercise().setGradingCriteria(gradingCriteria);
        // Remove sensitive information of submission depending on user
        mathSubmissionService.hideDetails(mathSubmission, userRepository.getUserWithGroupsAndAuthorities());

        return ResponseEntity.ok().body(mathSubmission);
    }
}
