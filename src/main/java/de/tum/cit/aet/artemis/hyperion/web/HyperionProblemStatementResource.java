package de.tum.cit.aet.artemis.hyperion.web;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGlobalRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementTargetedRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementGenerationService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRefinementService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRewriteService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for Hyperion problem statement features (generation,
 * refinement, rewrite, and consistency check).
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionProblemStatementResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementResource.class);

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final HyperionConsistencyCheckService consistencyCheckService;

    private final HyperionProblemStatementRewriteService problemStatementRewriteService;

    private final HyperionProblemStatementGenerationService problemStatementGenerationService;

    private final HyperionProblemStatementRefinementService problemStatementRefinementService;

    private final UserRepository userRepository;

    private final ExerciseVersionService exerciseVersionService;

    public HyperionProblemStatementResource(CourseRepository courseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionConsistencyCheckService consistencyCheckService, HyperionProblemStatementRewriteService problemStatementRewriteService,
            HyperionProblemStatementGenerationService problemStatementGenerationService, HyperionProblemStatementRefinementService problemStatementRefinementService,
            UserRepository userRepository, ExerciseVersionService exerciseVersionService) {
        this.courseRepository = courseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.consistencyCheckService = consistencyCheckService;
        this.problemStatementRewriteService = problemStatementRewriteService;
        this.problemStatementGenerationService = problemStatementGenerationService;
        this.problemStatementRefinementService = problemStatementRefinementService;
        this.userRepository = userRepository;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * POST programming-exercises/{programmingExerciseId}/consistency-check: Check
     * the consistency of a programming exercise.
     *
     * Returns a JSON body with the issues (can be empty list).
     *
     * @param exerciseId the id of the programming exercise to check
     * @return the ResponseEntity with status 200 (OK) and the consistency check
     *         result or an error status
     */
    @PostMapping("programming-exercises/{programmingExerciseId}/consistency-check")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ConsistencyCheckResponseDTO> checkExerciseConsistency(@PathVariable("programmingExerciseId") long exerciseId) {
        log.debug("REST request to Hyperion consistency check for programming exercise [{}]", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        var response = consistencyCheckService.checkConsistency(exercise);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates an exercise version if exerciseId is provided.
     * Validates that the exercise belongs to the specified course.
     *
     * @param exerciseId the optional exercise ID
     * @param courseId   the course ID to validate against
     */
    private void createExerciseVersionIfProvided(Long exerciseId, long courseId) {
        if (exerciseId != null) {
            ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
            // Security check: verify exercise belongs to the course
            Long exerciseCourseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
            if (!exerciseCourseId.equals(courseId)) {
                throw new BadRequestAlertException("Exercise does not belong to the specified course", "exercise", "exerciseNotInCourse");
            }
            User user = userRepository.getUserWithGroupsAndAuthorities();
            exerciseVersionService.createExerciseVersion(exercise, user);
            log.debug("Created exercise version for exercise [{}] after problem statement operation", exerciseId);
        }
    }

    /**
     * POST courses/{courseId}/problem-statements/rewrite: Rewrite a problem
     * statement for a course context.
     *
     * @param courseId the id of the course the problem statement belongs to
     * @param request  the request containing the original problem statement text
     * @return the ResponseEntity with status 200 (OK) and the rewritten problem
     *         statement or an error status
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/rewrite")
    public ResponseEntity<ProblemStatementRewriteResponseDTO> rewriteProblemStatement(@PathVariable long courseId, @RequestBody ProblemStatementRewriteRequestDTO request) {
        log.debug("REST request to Hyperion rewrite problem statement for course [{}]", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var result = problemStatementRewriteService.rewriteProblemStatement(course, request.problemStatementText());
        return ResponseEntity.ok(result);
    }

    /**
     * POST courses/{courseId}/problem-statements/generate: Generate a draft problem
     * statement for a programming exercise in the given course.
     *
     * @param courseId   the id of the course the problem statement belongs to
     * @param exerciseId optional exercise ID for versioning (null during exercise creation)
     * @param request    the request containing the user prompt
     * @return the ResponseEntity with status 200 (OK) and the generated draft
     *         problem statement or an error status
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/generate")
    public ResponseEntity<ProblemStatementGenerationResponseDTO> generateProblemStatement(@PathVariable long courseId, @RequestParam(required = false) Long exerciseId,
            @Valid @RequestBody ProblemStatementGenerationRequestDTO request) {
        log.debug("REST request to Hyperion generate draft problem statement for course [{}], exerciseId [{}]", courseId, exerciseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var result = problemStatementGenerationService.generateProblemStatement(course, request.userPrompt());
        createExerciseVersionIfProvided(exerciseId, courseId);
        return ResponseEntity.ok(result);
    }

    /**
     * POST courses/{courseId}/problem-statements/refine/global: Refine an existing
     * problem
     * statement using a global prompt.
     *
     * @param courseId   the id of the course the problem statement belongs to
     * @param exerciseId optional exercise ID for versioning (null during exercise creation)
     * @param request    the request containing the original problem statement and
     *                       user prompt
     * @return the ResponseEntity with status 200 (OK) and the refined problem
     *         statement or an error status
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/refine/global")
    public ResponseEntity<ProblemStatementRefinementResponseDTO> refineProblemStatementGlobally(@PathVariable long courseId, @RequestParam(required = false) Long exerciseId,
            @Valid @RequestBody ProblemStatementGlobalRefinementRequestDTO request) {
        log.debug("REST request to Hyperion refine the problem statement globally for course [{}], exerciseId [{}]", courseId, exerciseId);
        Course course = courseRepository.findByIdElseThrow(courseId);

        var result = problemStatementRefinementService.refineProblemStatement(course, request.problemStatementText(), request.userPrompt());
        createExerciseVersionIfProvided(exerciseId, courseId);

        return ResponseEntity.ok(result);
    }

    /**
     * POST courses/{courseId}/problem-statements/refine/targeted: Refine an
     * existing problem
     * statement using targeted instructions.
     *
     * @param courseId   the id of the course the problem statement belongs to
     * @param exerciseId optional exercise ID for versioning (null during exercise creation)
     * @param request    the request containing the original problem statement and
     *                       inline comments
     * @return the ResponseEntity with status 200 (OK) and the refined problem
     *         statement or an error status
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/refine/targeted")
    public ResponseEntity<ProblemStatementRefinementResponseDTO> refineProblemStatementTargeted(@PathVariable long courseId, @RequestParam(required = false) Long exerciseId,
            @Valid @RequestBody ProblemStatementTargetedRefinementRequestDTO request) {
        log.debug("REST request to Hyperion refine the problem statement with targeted instructions for course [{}], exerciseId [{}]", courseId, exerciseId);
        Course course = courseRepository.findByIdElseThrow(courseId);

        var result = problemStatementRefinementService.refineProblemStatementTargeted(course, request);
        createExerciseVersionIfProvided(exerciseId, courseId);

        return ResponseEntity.ok(result);
    }
}
