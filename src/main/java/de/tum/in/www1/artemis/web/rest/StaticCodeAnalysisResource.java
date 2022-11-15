package de.tum.in.www1.artemis.web.rest;

import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing static code analysis.
 * Static code analysis categories are created automatically when the programming exercise with static code analysis is
 * created, therefore a POST mapping is missing. A DELETE mapping is also not necessary as those categories can only be
 * deactivated but not deleted.
 */
@RestController
@RequestMapping("/api")
public class StaticCodeAnalysisResource {

    private static final String ENTITY_NAME = "StaticCodeAnalysisCategory";

    private final Logger log = LoggerFactory.getLogger(StaticCodeAnalysisResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    public StaticCodeAnalysisResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            StaticCodeAnalysisService staticCodeAnalysisService) {
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
    }

    /**
     * Get the static code analysis categories for a given exercise id.
     *
     * @param exerciseId of the exercise
     * @return the static code analysis categories
     */
    @GetMapping(Endpoints.CATEGORIES)
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Set<StaticCodeAnalysisCategory>> getStaticCodeAnalysisCategories(@PathVariable Long exerciseId) {
        log.debug("REST request to get static code analysis categories for programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        if (!Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
            throw new BadRequestAlertException("Static code analysis is not enabled", ENTITY_NAME, "staticCodeAnalysisNotEnabled");
        }

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);
        Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = staticCodeAnalysisService.findByExerciseId(exerciseId);
        return ResponseEntity.ok(staticCodeAnalysisCategories);
    }

    /**
     * Updates the static code analysis categories of a given programming exercise using the data in the request body.
     *
     * @param exerciseId of the exercise
     * @param categories used for the update
     * @return the updated static code analysis categories
     */
    @PatchMapping(Endpoints.CATEGORIES)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Set<StaticCodeAnalysisCategory>> updateStaticCodeAnalysisCategories(@PathVariable Long exerciseId,
            @RequestBody Set<StaticCodeAnalysisCategory> categories) {
        log.debug("REST request to update static code analysis categories for programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        if (!Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
            throw new BadRequestAlertException("Static code analysis is not enabled", ENTITY_NAME, "staticCodeAnalysisNotEnabled");
        }

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        validateCategories(categories, exerciseId);
        Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = staticCodeAnalysisService.updateCategories(exerciseId, categories);
        return ResponseEntity.ok(staticCodeAnalysisCategories);
    }

    /**
     * Reset the static code analysis categories of the given exercise to their default configuration.
     *
     * @param exerciseId if of the exercise for which the categories should be reset
     * @return static code analysis categories with the default configuration
     */
    @PatchMapping(Endpoints.RESET)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Set<StaticCodeAnalysisCategory>> resetStaticCodeAnalysisCategories(@PathVariable Long exerciseId) {
        log.debug("REST request to reset static code analysis categories for programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        if (!Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
            throw new BadRequestAlertException("Static code analysis is not enabled", ENTITY_NAME, "staticCodeAnalysisNotEnabled");
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = staticCodeAnalysisService.resetCategories(programmingExercise);
        return ResponseEntity.ok(staticCodeAnalysisCategories);
    }

    /**
     * Validates static code analysis categories
     *
     * @param categories to be validated
     * @param exerciseId path variable
     */
    private void validateCategories(Set<StaticCodeAnalysisCategory> categories, Long exerciseId) {
        for (var category : categories) {
            // Each category must have an id
            if (category.getId() == null) {
                throw new BadRequestAlertException("Static code analysis category id is missing.", ENTITY_NAME, "scaCategoryIdError");
            }

            // Penalty must not be null or negative
            if (category.getPenalty() == null || category.getPenalty() < 0) {
                throw new BadRequestAlertException("Penalty for static code analysis category " + category.getId() + " must be a non-negative integer.", ENTITY_NAME,
                        "scaCategoryPenaltyError");
            }

            // MaxPenalty must not be smaller than penalty
            if (category.getMaxPenalty() != null && category.getPenalty() > category.getMaxPenalty()) {
                throw new BadRequestAlertException("Max Penalty for static code analysis category " + category.getId() + " must not be smaller than the penalty.", ENTITY_NAME,
                        "scaCategoryMaxPenaltyError");
            }

            // Category state must not be null
            if (category.getState() == null) {
                throw new BadRequestAlertException("Max Penalty for static code analysis category " + category.getId() + " must not be smaller than the penalty.", ENTITY_NAME,
                        "scaCategoryStateError");
            }

            // Exercise id of the request path must match the exerciseId in the request body if present
            if (category.getExercise() != null && !Objects.equals(category.getExercise().getId(), exerciseId)) {
                throw new ConflictException("Exercise id path variable does not match exercise id of static code analysis category " + category.getId(), ENTITY_NAME,
                        "scaCategoryExerciseIdError");
            }
        }
    }

    public static final class Endpoints {

        private static final String PROGRAMMING_EXERCISE = "/programming-exercises/{exerciseId}";

        public static final String CATEGORIES = PROGRAMMING_EXERCISE + "/static-code-analysis-categories";

        public static final String RESET = PROGRAMMING_EXERCISE + "/static-code-analysis-categories/reset";

        private Endpoints() {
        }
    }
}
