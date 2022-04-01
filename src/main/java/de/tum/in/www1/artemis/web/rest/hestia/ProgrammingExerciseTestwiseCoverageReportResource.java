package de.tum.in.www1.artemis.web.rest.hestia;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestwiseCoverageReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTestwiseCoverageService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ProgrammingExerciseTestwiseCoverageReports and its entries.
 */
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseTestwiseCoverageReportResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestwiseCoverageReportResource.class);

    private final ProgrammingExerciseTestwiseCoverageService programmingExerciseTestwiseCoverageService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    public ProgrammingExerciseTestwiseCoverageReportResource(ProgrammingExerciseTestwiseCoverageService programmingExerciseTestwiseCoverageService,
            ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService) {
        this.programmingExerciseTestwiseCoverageService = programmingExerciseTestwiseCoverageService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * {@code GET exercises/:exerciseId/testwise-coverage-report} : Get the testwise coverage report for a programming exercise.
     *
     * @param exerciseId the exerciseId of the exercise of which to retrieve the testwise coverage report
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the testwise coverage report
     */
    @GetMapping("programming-exercises/{exerciseId}/testwise-coverage-report")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Set<ProgrammingExerciseTestwiseCoverageReport>> getTestwiseCoverageReports(@PathVariable Long exerciseId) {
        log.debug("REST request to get a ProgrammingExerciseTestwiseCoverageReport for exercise {}", exerciseId);

        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var reports = programmingExerciseTestwiseCoverageService.getTestwiseCoverageReportsForActiveAndBehaviorTestsForProgrammingExercise(exercise);

        if (reports.isEmpty()) {
            throw new EntityNotFoundException("Programming Exercise Testwise Coverage Report", exerciseId);
        }

        return ResponseEntity.ok(reports);
    }
}
