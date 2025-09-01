package de.tum.cit.aet.artemis.hyperion.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementRewriteService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for Hyperion Review & Refine features (consistency check and problem statement rewrite).
 *
 * Matches the OpenAPI specification under /api/hyperion/...
 */
@Profile(PROFILE_HYPERION)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionReviewAndRefineResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineResource.class);

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final HyperionConsistencyCheckService consistencyCheckService;

    private final HyperionProblemStatementRewriteService problemStatementRewriteService;

    public HyperionReviewAndRefineResource(UserRepository userRepository, CourseRepository courseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionConsistencyCheckService consistencyCheckService, HyperionProblemStatementRewriteService problemStatementRewriteService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.consistencyCheckService = consistencyCheckService;
        this.problemStatementRewriteService = problemStatementRewriteService;
    }

    /**
     * POST programming-exercises/{programmingExerciseId}/consistency-check: Check the consistency of a programming exercise.
     * Returns a JSON body with the issues (can be empty list).
     *
     * @param exerciseId the id of the programming exercise to check
     * @return the ResponseEntity with status 200 (OK) and the consistency check result or an error status
     */
    @PostMapping("programming-exercises/{programmingExerciseId}/consistency-check")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ConsistencyCheckResponseDTO> checkExerciseConsistency(@PathVariable("programmingExerciseId") long exerciseId) {
        log.debug("REST request to Hyperion consistency check for programming exercise [{}]", exerciseId);

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        try {
            var response = consistencyCheckService.checkConsistency(user, exercise);
            return ResponseEntity.ok(response);
        }
        catch (NetworkingException e) {
            log.warn("Hyperion service temporary unavailable during consistency check: {}", e.getMessage());
            return ResponseEntity.status(503).build();
        }
        catch (Exception e) {
            log.error("Unexpected error during Hyperion consistency check", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST courses/{courseId}/problem-statements/rewrite: Rewrite a problem statement for a course context.
     *
     * @param courseId the id of the course the problem statement belongs to
     * @param request  the request containing the original problem statement text
     * @return the ResponseEntity with status 200 (OK) and the rewritten problem statement or an error status
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/problem-statements/rewrite")
    public ResponseEntity<ProblemStatementRewriteResponseDTO> rewriteProblemStatement(@PathVariable long courseId, @RequestBody ProblemStatementRewriteRequestDTO request) {
        log.debug("REST request to Hyperion rewrite problem statement for course [{}]", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        try {
            var result = problemStatementRewriteService.rewriteProblemStatement(user, course, request.problemStatementText());
            return ResponseEntity.ok(result);
        }
        catch (NetworkingException e) {
            log.warn("Hyperion service temporary unavailable during rewrite: {}", e.getMessage());
            return ResponseEntity.status(503).build();
        }
        catch (Exception e) {
            log.error("Unexpected error during Hyperion rewrite", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
