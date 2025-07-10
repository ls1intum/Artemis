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

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.service.HyperionReviewAndRefineService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for reviewing and refining programming exercises using Hyperion.
 */
@Profile(PROFILE_HYPERION)
@RestController
@RequestMapping("api/hyperion/review-and-refine/")
@Lazy
public class HyperionReviewAndRefineResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionReviewAndRefineResource.class);

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final HyperionReviewAndRefineService reviewAndRefineService;

    public HyperionReviewAndRefineResource(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            HyperionReviewAndRefineService reviewAndRefineService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.reviewAndRefineService = reviewAndRefineService;
    }

    /**
     * POST /api/hyperion/review-and-refine/exercises/{exerciseId}/check-consistency :
     * Perform consistency check on a programming exercise using Hyperion.
     *
     * @param exerciseId the id of the exercise to check
     * @return the ResponseEntity with status 200 (OK) and the consistency check result
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
        catch (Exception e) {
            log.error("Consistency check failed for exercise {}: {}", exerciseId, e.getMessage(), e);
            throw e; // Re-throw to return proper HTTP error status
        }
    }

    /**
     * POST /api/hyperion/review-and-refine/courses/{courseId}/rewrite-problem-statement :
     * Rewrite a problem statement using Hyperion.
     *
     * @param request  the request containing the problem statement to be rewritten
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the rewritten problem statement
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
        catch (Exception e) {
            log.error("Problem statement rewriting failed for user {}: {}", user.getLogin(), e.getMessage(), e);
            throw e; // Re-throw to return proper HTTP error status
        }
    }

    /**
     * DTO for rewrite problem statement requests.
     *
     * @param text the problem statement text to be rewritten
     */
    public record RewriteProblemStatementRequestDTO(String text) {
    }
}
