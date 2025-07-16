package de.tum.cit.aet.artemis.hyperion.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.service.HyperionReviewAndRefineRestService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST endpoints for programming exercise review and enhancement using Hyperion service.
 *
 * Provides HTTP endpoints for consistency checking and problem statement enhancement.
 * All endpoints require appropriate instructor-level permissions and handle external
 * service integration with proper error mapping.
 */
@RestController
@Lazy
@Profile(PROFILE_HYPERION)
@RequestMapping("api/hyperion/review-and-refine/")
public class HyperionReviewAndRefineResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineResource.class);

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final HyperionReviewAndRefineRestService reviewAndRefineService;

    public HyperionReviewAndRefineResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionReviewAndRefineRestService reviewAndRefineService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.reviewAndRefineService = reviewAndRefineService;
    }

    /**
     * Analyzes programming exercise for consistency issues between problem statement,
     * template code, solution code, and test cases using Hyperion service.
     *
     * @param exerciseId the ID of the programming exercise to analyze
     * @return HTTP 200 with consistency analysis results, or appropriate error status
     */
    @EnforceAtLeastEditorInExercise
    @PostMapping("exercises/{exerciseId}/check-consistency")
    public ResponseEntity<String> checkExerciseConsistency(@PathVariable Long exerciseId) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        log.info("Performing consistency check for exercise {} by user {}", exerciseId, user.getLogin());

        try {
            String result = reviewAndRefineService.checkConsistency(user, programmingExercise);
            log.info("Consistency check completed successfully for exercise {}", exerciseId);
            return ResponseEntity.ok(result);
        }
        catch (NetworkingException e) {
            log.error("Consistency check failed for exercise {}: {}", exerciseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Service unavailable: " + e.getMessage());
        }
        catch (Exception e) {
            log.error("Consistency check failed for exercise {}: {}", exerciseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error: " + e.getMessage());
        }
    }

    /**
     * Enhances problem statement text using Hyperion service to improve clarity,
     * structure, and pedagogical value of exercise descriptions.
     *
     * @param request  the request containing the original problem statement text
     * @param courseId the ID of the course (for authorization context)
     * @return HTTP 200 with enhanced problem statement, or appropriate error status
     */
    @EnforceAtLeastEditorInCourse
    @PostMapping("courses/{courseId}/rewrite-problem-statement")
    public ResponseEntity<String> rewriteProblemStatement(@RequestBody RewriteProblemStatementRequestDTO request, @PathVariable Long courseId) {
        log.debug("REST request to rewrite problem statement via Hyperion: {}", request.text());

        var user = userRepository.getUserWithGroupsAndAuthorities();

        try {
            String rewrittenText = reviewAndRefineService.rewriteProblemStatement(user, request.text());
            log.info("Problem statement rewriting completed successfully for user {}", user.getLogin());
            return ResponseEntity.ok(rewrittenText);
        }
        catch (NetworkingException e) {
            log.error("Problem statement rewriting failed for user {}: {}", user.getLogin(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Service unavailable: " + e.getMessage());
        }
        catch (Exception e) {
            log.error("Problem statement rewriting failed for user {}: {}", user.getLogin(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error: " + e.getMessage());
        }
    }

    /**
     * Request DTO for problem statement enhancement operations.
     *
     * @param text the original problem statement text to be enhanced
     */
    public record RewriteProblemStatementRequestDTO(String text) {
    }
}
