package de.tum.cit.aet.artemis.hyperion.web;

import java.util.List;

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

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ReviewThreadSyncDTO;
import de.tum.cit.aet.artemis.exercise.service.ExerciseEditorSyncService;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.ChecklistSection;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGlobalRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementTargetedRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionChecklistService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementGenerationService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRefinementService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRewriteService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for the Hyperion problem statement features: generation, refinement, rewrite, checklist analysis, and consistency check.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionProblemStatementResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementResource.class);

    private final CourseRepository courseRepository;

    private final HyperionConsistencyCheckService consistencyCheckService;

    private final ExerciseReviewService exerciseReviewService;

    private final ExerciseEditorSyncService exerciseEditorSyncService;

    private final HyperionProblemStatementRewriteService problemStatementRewriteService;

    private final HyperionProblemStatementGenerationService problemStatementGenerationService;

    private final HyperionChecklistService checklistService;

    private final HyperionProblemStatementRefinementService problemStatementRefinementService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public HyperionProblemStatementResource(CourseRepository courseRepository, HyperionConsistencyCheckService consistencyCheckService, ExerciseReviewService exerciseReviewService,
            ExerciseEditorSyncService exerciseEditorSyncService, HyperionProblemStatementRewriteService problemStatementRewriteService,
            HyperionProblemStatementGenerationService problemStatementGenerationService, HyperionChecklistService checklistService,
            HyperionProblemStatementRefinementService problemStatementRefinementService, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.courseRepository = courseRepository;
        this.consistencyCheckService = consistencyCheckService;
        this.exerciseReviewService = exerciseReviewService;
        this.exerciseEditorSyncService = exerciseEditorSyncService;
        this.problemStatementRewriteService = problemStatementRewriteService;
        this.problemStatementGenerationService = problemStatementGenerationService;
        this.checklistService = checklistService;
        this.problemStatementRefinementService = problemStatementRefinementService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * POST programming-exercises/{exerciseId}/consistency-check : check the consistency of a programming exercise.
     *
     * @param exerciseId        the id of the programming exercise to check
     * @param skipThreadContext runs the check in isolation: earlier findings stay out of the prompts, and this run's findings are not turned into review threads
     * @return the issues found, possibly none
     */
    @PostMapping("programming-exercises/{exerciseId}/consistency-check")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ConsistencyCheckResponseDTO> checkExerciseConsistency(@PathVariable("exerciseId") long exerciseId,
            @RequestParam(required = false, defaultValue = "false") boolean skipThreadContext) {
        log.debug("REST request to Hyperion consistency check for programming exercise [{}]", exerciseId);
        ConsistencyCheckResponseDTO response = consistencyCheckService.checkConsistency(exerciseId, skipThreadContext);
        if (!skipThreadContext) {
            try {
                List<CommentThread> createdThreads = exerciseReviewService.createConsistencyCheckThreads(exerciseId, response.issues());
                for (CommentThread thread : createdThreads) {
                    CommentThreadDTO createdThread = new CommentThreadDTO(thread, CommentDTO.fromThread(thread));
                    exerciseEditorSyncService.broadcastReviewThreadUpdate(exerciseId, ReviewThreadSyncDTO.threadCreated(createdThread));
                }
            }
            catch (RuntimeException ex) {
                log.warn("Consistency check succeeded for exercise {}, but persisting review-comment threads failed", exerciseId, ex);
            }
        }
        return ResponseEntity.ok(response);
    }

    /**
     * POST courses/{courseId}/problem-statements/rewrite : rewrite a problem statement in the context of the given course.
     *
     * @param courseId the course whose title and description frame the rewrite
     * @param request  the original problem statement text
     * @return the rewritten problem statement
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
     * POST courses/{courseId}/problem-statements/generate : generate a draft problem statement for a programming exercise in the given course.
     *
     * @param courseId the course whose title and description frame the draft
     * @param request  the instructor's brief
     * @return the draft problem statement together with any non-blocking hygiene warnings about it
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
     * POST courses/{courseId}/checklist-analysis : analyse a problem statement for competencies, difficulty, and quality.
     * <p>
     * The three analyses run as concurrent LLM calls inside the service; joining them on the request thread costs nothing because Artemis serves requests on virtual threads.
     *
     * @param courseId the course the analysis is scoped to
     * @param request  the problem statement and its metadata, plus the exercise it belongs to if it is already persisted
     * @return the analysis of all three sections
     * @throws BadRequestAlertException if the request names an exercise that belongs to a different course
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/checklist-analysis")
    public ResponseEntity<ChecklistAnalysisResponseDTO> analyzeChecklist(@PathVariable long courseId, @Valid @RequestBody ChecklistAnalysisRequestDTO request) {
        log.debug("REST request to Hyperion checklist analysis for course [{}]", courseId);
        courseRepository.findByIdElseThrow(courseId);
        validateExerciseBelongsToCourse(request.exerciseId(), courseId);
        var result = checklistService.analyzeChecklist(request, courseId).join();
        return ResponseEntity.ok(result);
    }

    /**
     * POST courses/{courseId}/checklist-analysis/sections/{section} : re-run a single section of the checklist analysis.
     *
     * @param courseId the course the analysis is scoped to
     * @param section  the section to analyse
     * @param request  the problem statement and its metadata, plus the exercise it belongs to if it is already persisted
     * @return the analysis with only the requested section populated
     * @throws BadRequestAlertException if the request names an exercise that belongs to a different course
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/checklist-analysis/sections/{section}")
    public ResponseEntity<ChecklistAnalysisResponseDTO> analyzeChecklistSection(@PathVariable long courseId, @PathVariable ChecklistSection section,
            @Valid @RequestBody ChecklistAnalysisRequestDTO request) {
        log.debug("REST request to Hyperion checklist section analysis [{}] for course [{}]", section, courseId);
        courseRepository.findByIdElseThrow(courseId);
        validateExerciseBelongsToCourse(request.exerciseId(), courseId);
        var result = checklistService.analyzeSection(request, section, courseId).join();
        return ResponseEntity.ok(result);
    }

    /**
     * The course is what the caller's editor rights were checked against, so an exercise id in the body may not point somewhere else. A null id means the problem statement is not
     * persisted yet and there is nothing to cross-check.
     */
    private void validateExerciseBelongsToCourse(Long exerciseId, long courseId) {
        if (exerciseId != null) {
            ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
            Course exerciseCourse = exercise.getCourseViaExerciseGroupOrCourseMember();
            if (exerciseCourse == null || !exerciseCourse.getId().equals(courseId)) {
                throw new BadRequestAlertException("Exercise does not belong to the specified course", "exercise", "exerciseCourseMismatch");
            }
        }
    }

    /**
     * POST courses/{courseId}/checklist-actions : apply a checklist finding to the problem statement.
     *
     * @param courseId the course the action is scoped to
     * @param request  the action to apply and the context it applies to
     * @return the updated problem statement
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/checklist-actions")
    public ResponseEntity<ChecklistActionResponseDTO> applyChecklistAction(@PathVariable long courseId, @Valid @RequestBody ChecklistActionRequestDTO request) {
        log.debug("REST request to Hyperion checklist action [{}] for course [{}]", request.actionType(), courseId);
        courseRepository.findByIdElseThrow(courseId);
        var actionResult = checklistService.applyChecklistAction(request, courseId).join();
        return ResponseEntity.ok(actionResult);
    }

    /**
     * POST courses/{courseId}/problem-statements/refine/global : refine a whole problem statement against a free-text instruction.
     *
     * @param courseId the course whose title and description frame the refinement
     * @param request  the original problem statement and what to change about it
     * @return the refined problem statement, which is rejected as a bad request if it came back unchanged
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/refine/global")
    public ResponseEntity<ProblemStatementRefinementResponseDTO> refineProblemStatementGlobally(@PathVariable long courseId,
            @Valid @RequestBody ProblemStatementGlobalRefinementRequestDTO request) {
        log.debug("REST request to Hyperion refine the problem statement globally for course [{}]", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        var result = problemStatementRefinementService.refineProblemStatement(course, request.problemStatementText(), request.userPrompt());
        return ResponseEntity.ok(result);
    }

    /**
     * POST courses/{courseId}/problem-statements/refine/targeted : refine one selected range of a problem statement.
     *
     * @param courseId the course whose title and description frame the refinement
     * @param request  the original problem statement, the selected line/column range, and the instruction for it
     * @return the refined problem statement, which is rejected as a bad request if the range is out of bounds or the text came back unchanged
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/refine/targeted")
    public ResponseEntity<ProblemStatementRefinementResponseDTO> refineProblemStatementTargeted(@PathVariable long courseId,
            @Valid @RequestBody ProblemStatementTargetedRefinementRequestDTO request) {
        log.debug("REST request to Hyperion refine the problem statement with targeted instructions for course [{}]", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);

        var result = problemStatementRefinementService.refineProblemStatementTargeted(course, request);

        return ResponseEntity.ok(result);
    }

}
