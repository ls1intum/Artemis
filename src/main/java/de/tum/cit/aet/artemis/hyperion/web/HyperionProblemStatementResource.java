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
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionChecklistService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementGenerationService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRewriteService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for Hyperion problem statement features (generation, rewrite,
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

    private final HyperionChecklistService checklistService;

    public HyperionProblemStatementResource(CourseRepository courseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionConsistencyCheckService consistencyCheckService, HyperionProblemStatementRewriteService problemStatementRewriteService,
            HyperionProblemStatementGenerationService problemStatementGenerationService, HyperionChecklistService checklistService) {
        this.courseRepository = courseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.consistencyCheckService = consistencyCheckService;
        this.problemStatementRewriteService = problemStatementRewriteService;
        this.problemStatementGenerationService = problemStatementGenerationService;
        this.checklistService = checklistService;
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
     * POST courses/{courseId}/checklist-analysis: Analyze the problem statement
     * for checklist (learning goals, difficulty, quality).
     *
     * @param courseId the id of the course
     * @param request  the request containing problem statement, metadata, and
     *                     an optional exerciseId
     * @return the checklist analysis result
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/checklist-analysis")
    public ResponseEntity<ChecklistAnalysisResponseDTO> analyzeChecklist(@PathVariable long courseId, @Valid @RequestBody ChecklistAnalysisRequestDTO request) {
        log.debug("REST request to Hyperion checklist analysis for course [{}]", courseId);
        courseRepository.findByIdElseThrow(courseId);
        if (request.exerciseId() != null) {
            ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(request.exerciseId());
            Course exerciseCourse = exercise.getCourseViaExerciseGroupOrCourseMember();
            if (exerciseCourse == null || !exerciseCourse.getId().equals(courseId)) {
                throw new BadRequestAlertException("Exercise does not belong to the specified course", "exercise", "exerciseCourseMismatch");
            }
        }
        var result = checklistService.analyzeChecklist(request);
        return ResponseEntity.ok(result);
    }

    /**
     * POST courses/{courseId}/checklist-actions: Apply an AI-powered checklist
     * action to modify the problem statement.
     * <p>
     * Note: This endpoint only transforms the problem statement text via AI and does
     * not access any exercise data. Course-level editor authorization is sufficient
     * because no exercise-specific resources are read or modified.
     *
     * @param courseId the id of the course
     * @param request  the action request containing the action type and context
     * @return the response containing the updated problem statement
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/checklist-actions")
    public ResponseEntity<ChecklistActionResponseDTO> applyChecklistAction(@PathVariable long courseId, @Valid @RequestBody ChecklistActionRequestDTO request) {
        log.debug("REST request to Hyperion checklist action [{}] for course [{}]", request.actionType(), courseId);
        courseRepository.findByIdElseThrow(courseId);
        var actionResult = checklistService.applyChecklistAction(request);
        return ResponseEntity.ok(actionResult);
    }
}
