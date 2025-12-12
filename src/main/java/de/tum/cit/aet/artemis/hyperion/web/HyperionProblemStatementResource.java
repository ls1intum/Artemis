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
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementGenerationService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRefinementService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRewriteService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for Hyperion problem statement features (generation, refinement, rewrite,
 * and consistency check).
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

    public HyperionProblemStatementResource(CourseRepository courseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionConsistencyCheckService consistencyCheckService, HyperionProblemStatementRewriteService problemStatementRewriteService,
            HyperionProblemStatementGenerationService problemStatementGenerationService, HyperionProblemStatementRefinementService problemStatementRefinementService) {
        this.courseRepository = courseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.consistencyCheckService = consistencyCheckService;
        this.problemStatementRewriteService = problemStatementRewriteService;
        this.problemStatementGenerationService = problemStatementGenerationService;
        this.problemStatementRefinementService = problemStatementRefinementService;
    }

    /**
     * POST programming-exercises/{programmingExerciseId}/consistency-check: Check
     * the consistency of a programming exercise.
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
     * @param courseId the id of the course the problem statement belongs to
     * @param request  the request containing the user prompt
     * @return the ResponseEntity with status 200 (OK) and the generated draft
     *         problem statement or an error status
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/generate")
    public ResponseEntity<ProblemStatementGenerationResponseDTO> generateProblemStatement(@PathVariable long courseId,
            @Valid @RequestBody ProblemStatementGenerationRequestDTO request) {
        log.debug("REST request to Hyperion generate draft problem statement for course [{}]", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var result = problemStatementGenerationService.generateProblemStatement(course, request.userPrompt());
        return ResponseEntity.ok(result);
    }

    /**
     * POST courses/{courseId}/problem-statements/refine: Refine an existing problem
     * statement for a programming exercise in the given course.
     * Supports two modes:
     * 1. Global refinement: userPrompt is provided
     * 2. Targeted refinement: inlineComments are provided
     *
     * @param courseId the id of the course the problem statement belongs to
     * @param request  the request containing the original problem statement and
     *                     either userPrompt or inlineComments
     * @return the ResponseEntity with status 200 (OK) and the refined problem
     *         statement or an error status
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/refine")
    public ResponseEntity<ProblemStatementRefinementResponseDTO> refineProblemStatement(@PathVariable long courseId,
            @Valid @RequestBody ProblemStatementRefinementRequestDTO request) {
        log.debug("REST request to Hyperion refine the problem statement for course [{}]", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);

        ProblemStatementRefinementResponseDTO result;
        if (request.hasInlineComments()) {
            // Targeted refinement with inline comments
            result = problemStatementRefinementService.refineProblemStatementWithComments(course, request.problemStatementText(), request.inlineComments());
        }
        else {
            // Global refinement with user prompt
            result = problemStatementRefinementService.refineProblemStatement(course, request.problemStatementText(), request.userPrompt());
        }

        return ResponseEntity.ok(result);
    }
}
