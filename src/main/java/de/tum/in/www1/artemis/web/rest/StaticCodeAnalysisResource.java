package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.service.UserService;

/**
 * REST controller for managing static code analysis.
 * Static code analysis categories are created automatically when the programming exercise with static code analysis is
 * created, therefore a POST mapping is missing. A DELETE mapping is also not necessary as those categories can only be
 * deactivated but not deleted.
 */
@RestController
@RequestMapping("/api")
public class StaticCodeAnalysisResource {

    private final Logger log = LoggerFactory.getLogger(StaticCodeAnalysisResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    public StaticCodeAnalysisResource(AuthorizationCheckService authCheckService, UserService userService, ProgrammingExerciseService programmingExerciseService,
            StaticCodeAnalysisService staticCodeAnalysisService) {
        this.authCheckService = authCheckService;
        this.programmingExerciseService = programmingExerciseService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
    }

    /**
     * Get the static code analysis categories for a given exercise id.
     *
     * @param exerciseId of the the exercise
     * @return the static code analysis categories
     */
    @GetMapping(Endpoints.CATEGORIES)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<StaticCodeAnalysisCategory>> getStaticCodeAnalysisCategories(@PathVariable Long exerciseId) {
        log.debug("REST request to get static code analysis categories for programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseService.findById(exerciseId);

        if (!Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
            return badRequest();
        }

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            return forbidden();
        }

        Set<StaticCodeAnalysisCategory> staticCodeAnalysisCategories = staticCodeAnalysisService.findByExerciseId(exerciseId);
        return ResponseEntity.ok(staticCodeAnalysisCategories);
    }

    public static final class Endpoints {

        private static final String PROGRAMMING_EXERCISE = "/programming-exercise/{exerciseId}";

        public static final String CATEGORIES = PROGRAMMING_EXERCISE + "/static-code-analysis-categories";

        private Endpoints() {
        }
    }
}
