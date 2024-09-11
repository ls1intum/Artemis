package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;

/**
 * REST controller for managing static code analysis.
 * Static code analysis categories are created automatically when the programming exercise with static code analysis is
 * created, therefore a POST mapping is missing. A DELETE mapping is also not necessary as those categories can only be
 * deactivated but not deleted.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class StaticCodeAnalysisResource {

    private static final String ENTITY_NAME = "StaticCodeAnalysisCategory";

    private static final Logger log = LoggerFactory.getLogger(StaticCodeAnalysisResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    public StaticCodeAnalysisResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            StaticCodeAnalysisService staticCodeAnalysisService, StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository) {
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
    }

    /**
     * Get the static code analysis categories for a given exercise id.
     *
     * @param exerciseId of the exercise
     * @return the static code analysis categories
     */
    @GetMapping("programming-exercises/{exerciseId}/static-code-analysis-categories")
    @EnforceAtLeastTutor
    public ResponseEntity<Set<StaticCodeAnalysisCategory>> getStaticCodeAnalysisCategories(@PathVariable Long exerciseId) {
        log.debug("REST request to get static code analysis categories for programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        checkSCAEnabledForExerciseElseThrow(programmingExercise);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);

        Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(staticCodeAnalysisCategories);
    }

    /**
     * Updates the static code analysis categories of a given programming exercise using the data in the request body.
     *
     * @param exerciseId of the exercise
     * @param categories used for the update
     * @return the updated static code analysis categories
     */
    @PatchMapping("programming-exercises/{exerciseId}/static-code-analysis-categories")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<StaticCodeAnalysisCategory>> updateStaticCodeAnalysisCategories(@PathVariable Long exerciseId,
            @RequestBody Set<StaticCodeAnalysisCategory> categories) {
        log.debug("REST request to update static code analysis categories for programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        checkSCAEnabledForExerciseElseThrow(programmingExercise);
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
    @PatchMapping("programming-exercises/{exerciseId}/static-code-analysis-categories/reset")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<StaticCodeAnalysisCategory>> resetStaticCodeAnalysisCategories(@PathVariable Long exerciseId) {
        log.debug("REST request to reset static code analysis categories for programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        checkSCAEnabledForExerciseElseThrow(programmingExercise);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = staticCodeAnalysisService.resetCategories(programmingExercise);
        return ResponseEntity.ok(staticCodeAnalysisCategories);
    }

    /**
     * PATCH /programming-exercises/:exerciseId/static-code-analysis-categories/import
     *
     * @param exerciseId       The exercise to copy the configuration into
     * @param sourceExerciseId The exercise to take the existing configuration from
     * @return The newly created SCA configuration
     * @see StaticCodeAnalysisService#importCategoriesFromExercise(ProgrammingExercise, ProgrammingExercise)
     */
    @PatchMapping("programming-exercises/{exerciseId}" + "/static-code-analysis-categories/import")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<StaticCodeAnalysisCategory>> importStaticCodeAnalysisCategoriesFromExercise(@PathVariable Long exerciseId, @RequestParam Long sourceExerciseId) {
        log.debug("REST request to import static code analysis categories to programming exercise {} from exercise {}", exerciseId, sourceExerciseId);

        ProgrammingExercise targetExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        checkSCAEnabledForExerciseElseThrow(targetExercise);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, targetExercise, null);

        ProgrammingExercise sourceExercise = programmingExerciseRepository.findByIdElseThrow(sourceExerciseId);
        checkSCAEnabledForExerciseElseThrow(sourceExercise);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, sourceExercise, null);

        if (targetExercise.getProgrammingLanguage() != sourceExercise.getProgrammingLanguage()) {
            throw new ConflictException("SCA configurations can only be imported from exercises with the same programming language", ENTITY_NAME, "programmingLanguageMismatch");
        }

        Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = staticCodeAnalysisService.importCategoriesFromExercise(sourceExercise, targetExercise);
        return ResponseEntity.ok(staticCodeAnalysisCategories);
    }

    private void checkSCAEnabledForExerciseElseThrow(ProgrammingExercise programmingExercise) {
        if (!Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
            throw new BadRequestAlertException("Static code analysis is not enabled", ENTITY_NAME, "staticCodeAnalysisNotEnabled");
        }
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
                throw new BadRequestAlertException("Penalty for static code analysis category " + category.getName() + " must be a non-negative integer.", ENTITY_NAME,
                        "scaCategoryPenaltyError");
            }

            // MaxPenalty must not be smaller than penalty
            if (category.getMaxPenalty() != null && category.getPenalty() > category.getMaxPenalty()) {
                throw new BadRequestAlertException("Max Penalty for static code analysis category " + category.getName() + " must not be smaller than the penalty.", ENTITY_NAME,
                        "scaCategoryMaxPenaltyError");
            }

            // Category state must not be null
            if (category.getState() == null) {
                throw new BadRequestAlertException("Max Penalty for static code analysis category " + category.getName() + " must not be smaller than the penalty.", ENTITY_NAME,
                        "scaCategoryStateError");
            }

            // Exercise id of the request path must match the exerciseId in the request body if present
            if (category.getExercise() != null && !Objects.equals(category.getExercise().getId(), exerciseId)) {
                throw new ConflictException("Exercise id path variable does not match exercise id of static code analysis category " + category.getName(), ENTITY_NAME,
                        "scaCategoryExerciseIdError");
            }
        }
    }
}
