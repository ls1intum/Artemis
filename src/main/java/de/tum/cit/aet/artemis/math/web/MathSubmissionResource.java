package de.tum.cit.aet.artemis.math.web;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.HintRequestDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;
import de.tum.cit.aet.artemis.math.grader.HintSuggestion;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;
import de.tum.cit.aet.artemis.math.service.MathGradingService;

@Lazy
@Conditional(MathEnabled.class)
@RestController
@RequestMapping("api/math/")
public class MathSubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(MathSubmissionResource.class);

    private final MathSubmissionRepository mathSubmissionRepository;

    private final MathExerciseRepository mathExerciseRepository;

    private final ResultRepository resultRepository;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authCheckService;

    private final MathGradingService mathGradingService;

    public MathSubmissionResource(MathSubmissionRepository mathSubmissionRepository, MathExerciseRepository mathExerciseRepository, ResultRepository resultRepository,
            UserRepository userRepository, StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService,
            MathGradingService mathGradingService) {
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.mathExerciseRepository = mathExerciseRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authCheckService = authCheckService;
        this.mathGradingService = mathGradingService;
    }

    @PostMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> createMathSubmission(@PathVariable Long exerciseId, @RequestBody MathSubmissionDTO mathSubmissionDTO) {
        log.debug("REST request to save MathSubmission for exercise : {}", exerciseId);
        return ResponseEntity.ok(saveAndEvaluate(exerciseId, mathSubmissionDTO));
    }

    @PutMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> updateMathSubmission(@PathVariable Long exerciseId, @RequestBody MathSubmissionDTO mathSubmissionDTO) {
        log.debug("REST request to update MathSubmission for exercise : {}", exerciseId);
        return ResponseEntity.ok(saveAndEvaluate(exerciseId, mathSubmissionDTO));
    }

    private MathSubmissionDTO saveAndEvaluate(Long exerciseId, MathSubmissionDTO dto) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        MathExercise mathExercise = mathExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        StudentParticipation participation = studentParticipationRepository.findFirstByExerciseIdAndStudentLoginOrderByIdDesc(exerciseId, user.getLogin()).orElseThrow();

        MathSubmission submission = dto.toEntity();
        submission.setParticipation(participation);
        for (DerivationStep step : submission.getSteps()) {
            step.setSubmission(submission);
            try {
                MathNodes.assertWildcardFree(step.getResultExpression());
            }
            catch (IllegalArgumentException e) {
                throw new BadRequestAlertException(e.getMessage(), "mathSubmission", "wildcardNotAllowed");
            }
            step.setResultExpression(MathNodes.normalize(step.getResultExpression()));
        }

        submission = mathSubmissionRepository.save(submission);
        submission = mathSubmissionRepository.findByIdWithStepsAndResults(submission.getId()).orElseThrow();

        if (Boolean.TRUE.equals(submission.isSubmitted())) {
            Result result = new Result();
            result.setSubmission(submission);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(ZonedDateTime.now());
            result.setRated(true);
            result.setExerciseId(exerciseId);

            double score = mathGradingService.gradeSubmission(mathExercise, submission);
            result.setScore(score, mathExercise.getCourseViaExerciseGroupOrCourseMember());

            resultRepository.save(result);
            submission.addResult(result);
        }

        participation.setExercise(mathExercise);
        submission.setParticipation(participation);
        return MathSubmissionDTO.of(submission);
    }

    /**
     * GET /participations/:participationId/math-editor : Returns the data needed for the math editor,
     * including the participation, the latest MathSubmission, and results.
     *
     * @param participationId the participation for which to load editor data
     * @return ResponseEntity with the latest MathSubmission (empty if no submission yet)
     */
    @GetMapping("participations/{participationId}/math-editor")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> getDataForMathEditor(@PathVariable Long participationId) {
        log.debug("REST request to get math editor data for participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithLatestSubmissionsResultsFeedbackElseThrow(participationId);

        if (!(participation.getExercise() instanceof MathExercise mathExercise)) {
            throw new IllegalArgumentException("Participation does not belong to a math exercise");
        }
        // Reload with categories to avoid LazyInitializationException when DTO is serialized/logged
        mathExercise = mathExerciseRepository.findByIdWithCategories(mathExercise.getId()).orElseThrow();
        participation.setExercise(mathExercise);
        if (!(authCheckService.isOwnerOfParticipation(participation) || authCheckService.isAtLeastTeachingAssistantForExercise(mathExercise))) {
            throw new AccessForbiddenException("participation", participationId);
        }

        Optional<MathSubmission> latestSubmission = participation.findLatestSubmission().filter(s -> s instanceof MathSubmission).map(s -> (MathSubmission) s);

        MathSubmission submission;
        submission = latestSubmission.map(mathSubmission -> mathSubmissionRepository.findByIdWithStepsAndResults(mathSubmission.getId()).orElseThrow())
                .orElseGet(MathSubmission::new);
        submission.setParticipation(participation);
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }

    @GetMapping("math-submissions/{submissionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> getMathSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get MathSubmission : {}", submissionId);
        MathSubmission submission = mathSubmissionRepository.findById(submissionId).orElseThrow();
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }

    /**
     * GET /math-submissions/{submissionId}/for-assessment : load a submission for tutor assessment,
     * eagerly fetching steps, results, and participation.
     *
     * @param submissionId the submission to load
     * @return the submission populated for the assessment view
     */
    @GetMapping("math-submissions/{submissionId}/for-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<MathSubmissionDTO> getMathSubmissionForAssessment(@PathVariable Long submissionId) {
        log.debug("REST request to get MathSubmission for assessment : {}", submissionId);
        MathSubmission submission = mathSubmissionRepository.findByIdWithStepsResultsAndParticipation(submissionId).orElseThrow();
        if (submission.getParticipation() != null && submission.getParticipation().getExercise() instanceof MathExercise pe) {
            MathExercise exerciseWithCategories = mathExerciseRepository.findByIdWithCategories(pe.getId()).orElseThrow();
            submission.getParticipation().setExercise(exerciseWithCategories);
        }
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }

    /**
     * GET /exercises/{exerciseId}/math-submissions : list all submitted submissions for an exercise.
     *
     * @param exerciseId the exercise whose submissions to list
     * @return submitted submissions for the exercise
     */
    @GetMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<MathSubmissionDTO>> getSubmittedMathSubmissions(@PathVariable Long exerciseId) {
        log.debug("REST request to get submitted MathSubmissions for exercise : {}", exerciseId);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        List<MathSubmissionDTO> dtos = mathSubmissionRepository.findSubmittedByExerciseId(exerciseId).stream().map(MathSubmissionDTO::of).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /exercises/{exerciseId}/hints : ranked next-step suggestions for the student's current math state.
     * Gated by {@link MathExercise#isAllowVerification()} — instructors can disable hints per exercise.
     *
     * @param exerciseId the exercise the student is working on
     * @param request    the hint request body carrying the current expression
     * @return up to three {@link HintSuggestion}s ranked by progress toward the goal
     */
    @PostMapping("exercises/{exerciseId}/hints")
    @EnforceAtLeastStudent
    public ResponseEntity<List<HintSuggestion>> suggestHints(@PathVariable Long exerciseId, @RequestBody HintRequestDTO request) {
        log.debug("REST request to compute hints for math exercise : {}", exerciseId);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        if (!exercise.isAllowVerification()) {
            throw new AccessForbiddenException("Hint generation is disabled for this exercise.");
        }
        try {
            MathNodes.assertWildcardFree(request.currentExpression());
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), "mathSubmission", "wildcardNotAllowed");
        }
        return ResponseEntity.ok(mathGradingService.suggestHints(exercise, MathNodes.normalize(request.currentExpression())));
    }

    /**
     * PUT /math-submissions/{submissionId}/manual-result : overwrite the latest result with a
     * tutor-supplied manual score.
     *
     * @param submissionId the submission to assess
     * @param score        the manual score in [0, 100]
     * @return the submission with the new manual result attached
     */
    @PutMapping("math-submissions/{submissionId}/manual-result")
    @EnforceAtLeastTutor
    public ResponseEntity<MathSubmissionDTO> saveManualResult(@PathVariable Long submissionId, @RequestBody double score) {
        log.debug("REST request to save manual result for MathSubmission : {}", submissionId);
        MathSubmission submission = mathSubmissionRepository.findByIdWithStepsResultsAndParticipation(submissionId).orElseThrow();
        MathExercise exercise = (MathExercise) submission.getParticipation().getExercise();

        Result result = submission.getLatestResult();
        if (result == null || result.getAssessmentType() != AssessmentType.MANUAL) {
            result = new Result();
            result.setSubmission(submission);
            result.setAssessmentType(AssessmentType.MANUAL);
            result.setRated(true);
            result.setExerciseId(exercise.getId());
        }
        result.setCompletionDate(ZonedDateTime.now());
        result.setScore(score, exercise.getCourseViaExerciseGroupOrCourseMember());
        resultRepository.save(result);
        submission.addResult(result);

        submission = mathSubmissionRepository.findByIdWithStepsResultsAndParticipation(submissionId).orElseThrow();
        if (submission.getParticipation() != null && submission.getParticipation().getExercise() instanceof MathExercise pe) {
            MathExercise exerciseWithCategories = mathExerciseRepository.findByIdWithCategories(pe.getId()).orElseThrow();
            submission.getParticipation().setExercise(exerciseWithCategories);
        }
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }
}
