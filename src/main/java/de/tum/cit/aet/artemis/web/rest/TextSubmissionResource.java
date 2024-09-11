package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ResultService;
import de.tum.cit.aet.artemis.service.TextAssessmentService;
import de.tum.cit.aet.artemis.service.TextSubmissionService;
import de.tum.cit.aet.artemis.service.exam.ExamSubmissionService;
import de.tum.cit.aet.artemis.service.plagiarism.PlagiarismService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing TextSubmission.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class TextSubmissionResource extends AbstractSubmissionResource {

    private static final String ENTITY_NAME = "textSubmission";

    private static final Logger log = LoggerFactory.getLogger(TextSubmissionResource.class);

    private final TextSubmissionRepository textSubmissionRepository;

    private final ExerciseRepository exerciseRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final TextSubmissionService textSubmissionService;

    private final TextAssessmentService textAssessmentService;

    private final UserRepository userRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ExamSubmissionService examSubmissionService;

    private final PlagiarismService plagiarismService;

    public TextSubmissionResource(SubmissionRepository submissionRepository, ResultService resultService, TextSubmissionRepository textSubmissionRepository,
            ExerciseRepository exerciseRepository, TextExerciseRepository textExerciseRepository, AuthorizationCheckService authCheckService,
            TextSubmissionService textSubmissionService, UserRepository userRepository, StudentParticipationRepository studentParticipationRepository,
            GradingCriterionRepository gradingCriterionRepository, TextAssessmentService textAssessmentService, ExamSubmissionService examSubmissionService,
            PlagiarismService plagiarismService) {
        super(submissionRepository, authCheckService, userRepository, exerciseRepository, textSubmissionService, studentParticipationRepository);
        this.textSubmissionRepository = textSubmissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.authCheckService = authCheckService;
        this.textSubmissionService = textSubmissionService;
        this.userRepository = userRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.textAssessmentService = textAssessmentService;
        this.examSubmissionService = examSubmissionService;
        this.plagiarismService = plagiarismService;
    }

    /**
     * POST /exercises/{exerciseId}/text-submissions : Create a new textSubmission. This is called when a student saves his/her answer
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param textSubmission the textSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/text-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<TextSubmission> createTextSubmission(@PathVariable Long exerciseId, @Valid @RequestBody TextSubmission textSubmission) {
        log.debug("REST request to save text submission : {}", textSubmission);
        if (textSubmission.getId() != null) {
            throw new BadRequestAlertException("A new text submission cannot already have an ID", ENTITY_NAME, "idExists");
        }
        return handleTextSubmission(exerciseId, textSubmission);
    }

    /**
     * PUT /exercises/{exerciseId}/text-submissions : Updates an existing text submission or creates a new one.
     * This function is called by the text editor for saving and submitting text submissions.
     * The submit specific handling occurs in the TextSubmissionService.createTextSubmission() and save() methods.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param textSubmission the textSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textSubmission, or with status 400 (Bad Request) if the textSubmission is not valid, or with status
     *         500 (Internal Server Error) if the textSubmission couldn't be updated
     */
    @PutMapping("exercises/{exerciseId}/text-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<TextSubmission> updateTextSubmission(@PathVariable long exerciseId, @Valid @RequestBody TextSubmission textSubmission) {
        log.debug("REST request to update text submission: {}", textSubmission);
        if (textSubmission.getId() == null) {
            return createTextSubmission(exerciseId, textSubmission);
        }
        return handleTextSubmission(exerciseId, textSubmission);
    }

    @NotNull
    private ResponseEntity<TextSubmission> handleTextSubmission(long exerciseId, TextSubmission textSubmission) {
        long start = System.currentTimeMillis();
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var exercise = textExerciseRepository.findByIdElseThrow(exerciseId);

        // Apply further checks if it is an exam submission
        examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, user);

        // Prevent multiple submissions (currently only for exam submissions)
        textSubmission = (TextSubmission) examSubmissionService.preventMultipleSubmissions(exercise, textSubmission, user);
        // Check if the user is allowed to submit
        textSubmissionService.checkSubmissionAllowanceElseThrow(exercise, textSubmission, user);

        textSubmission = textSubmissionService.handleTextSubmission(textSubmission, exercise, user);
        textSubmissionService.hideDetails(textSubmission, user);
        long end = System.currentTimeMillis();
        log.info("handleTextSubmission took {}ms for exercise {} and user {}", end - start, exerciseId, user.getLogin());
        return ResponseEntity.ok(textSubmission);
    }

    /**
     * GET /text-submissions/:id : get the "id" textSubmission.
     *
     * @param submissionId the id of the textSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the textSubmission, or with status 404 (Not Found)
     */
    @GetMapping("text-submissions/{submissionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<TextSubmission> getTextSubmissionWithResults(@PathVariable long submissionId) {
        log.debug("REST request to get text submission: {}", submissionId);
        var textSubmission = textSubmissionRepository.findWithEagerResultsById(submissionId).orElseThrow(() -> new EntityNotFoundException("TextSubmission", submissionId));

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(textSubmission.getParticipation().getExercise())) {
            // anonymize and throw exception if not authorized to view submission
            plagiarismService.checkAccessAndAnonymizeSubmissionForStudent(textSubmission, userRepository.getUser().getLogin(), textSubmission.getParticipation());
            return ResponseEntity.ok(textSubmission);
        }

        return ResponseEntity.ok().body(textSubmission);
    }

    /**
     * GET /text-submissions : get all the textSubmissions for an exercise. It is possible to filter, to receive only the one that have been already submitted,
     * or only the one assessed by the tutor who is doing the call.
     * In case of exam exercise, it filters out all test run submissions.
     *
     * @param exerciseId      exerciseID for which all submissions should be returned
     * @param correctionRound get submission with results in the correction round
     * @param submittedOnly   mark if only submitted Submissions should be returned
     * @param assessedByTutor mark if only assessed Submissions should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping("exercises/{exerciseId}/text-submissions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Submission>> getAllTextSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get all TextSubmissions");
        return super.getAllSubmissions(exerciseId, submittedOnly, assessedByTutor, correctionRound);
    }

    /**
     * GET /text-submission-without-assessment : get one textSubmission without assessment.
     *
     * @param exerciseId                      exerciseID for which a submission should be returned
     * @param correctionRound                 correctionRound for which submissions without a result should be returned
     *                                            TODO: Replace ?head=true with HTTP HEAD request
     * @param skipAssessmentOrderOptimization optional value to define if the assessment queue should be skipped. Use if only checking for needed assessments.
     * @param lockSubmission                  optional value to define if the submission should be locked and has the value of false if not set manually
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping("exercises/{exerciseId}/text-submission-without-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<TextSubmission> getTextSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "head", defaultValue = "false") boolean skipAssessmentOrderOptimization,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get a text submission without assessment");
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        if (!(exercise instanceof TextExercise)) {
            throw new BadRequestAlertException("The exerciseId does not belong to a text exercise", ENTITY_NAME, "wrongExerciseType");
        }

        // Check if tutors can start assessing the students submission
        this.textSubmissionService.checkIfExerciseDueDateIsReached(exercise);

        // Check if the limit of simultaneously locked submissions has been reached
        textSubmissionService.checkSubmissionLockLimit(exercise.getCourseViaExerciseGroupOrCourseMember().getId());

        Optional<TextSubmission> optionalTextSubmission = textSubmissionService.getRandomTextSubmissionEligibleForNewAssessment((TextExercise) exercise,
                skipAssessmentOrderOptimization, exercise.isExamExercise(), correctionRound);

        // No more unassessed submissions
        if (optionalTextSubmission.isEmpty()) {
            return ResponseEntity.ok(null);
        }

        final TextSubmission textSubmission = optionalTextSubmission.get();

        if (lockSubmission) {
            textSubmissionService.lockTextSubmissionToBeAssessed(optionalTextSubmission.get(), correctionRound);
            textAssessmentService.prepareSubmissionForAssessment(textSubmission, textSubmission.getResultForCorrectionRound(correctionRound));
        }

        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);

        // Make sure the exercise is connected to the participation in the json response
        final StudentParticipation studentParticipation = (StudentParticipation) textSubmission.getParticipation();
        studentParticipation.setExercise(exercise);
        textSubmission.getParticipation().getExercise().setGradingCriteria(gradingCriteria);
        // Remove sensitive information of submission depending on user
        textSubmissionService.hideDetails(textSubmission, userRepository.getUserWithGroupsAndAuthorities());

        return ResponseEntity.ok().body(textSubmission);
    }
}
