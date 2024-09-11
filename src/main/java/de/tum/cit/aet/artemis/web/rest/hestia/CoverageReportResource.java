package de.tum.cit.aet.artemis.web.rest.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.programming.domain.hestia.CoverageReport;
import de.tum.cit.aet.artemis.programming.service.hestia.TestwiseCoverageService;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ProgrammingExerciseTestwiseCoverageReports and its entries.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class CoverageReportResource {

    private static final Logger log = LoggerFactory.getLogger(CoverageReportResource.class);

    private final TestwiseCoverageService testwiseCoverageService;

    public CoverageReportResource(TestwiseCoverageService testwiseCoverageService) {
        this.testwiseCoverageService = testwiseCoverageService;
    }

    /**
     * {@code GET exercises/:exerciseId/full-testwise-coverage-report} : Get the latest coverage report for a solution submission
     * of a programming exercise with all file reports and descendants.
     *
     * @param exerciseId the exerciseId of the exercise of which to retrieve the testwise coverage report for the latest solution submission
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the coverage report
     */
    @GetMapping("programming-exercises/{exerciseId}/full-testwise-coverage-report")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<CoverageReport> getLatestFullCoverageReport(@PathVariable Long exerciseId) {
        log.debug("REST request to get the latest Full Testwise CoverageReport for exercise {}", exerciseId);

        var optionalReportWithFileReports = testwiseCoverageService.getFullCoverageReportForLatestSolutionSubmissionFromProgrammingExercise(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Coverage report for exercise " + exerciseId + " not found."));
        return ResponseEntity.ok(optionalReportWithFileReports);
    }

    /**
     * {@code GET exercises/:exerciseId/testwise-coverage-report} : Get the latest coverage report for a solution submission
     * of a programming exercise without the actual file reports.
     *
     * @param exerciseId the exerciseId of the exercise of which to retrieve the testwise coverage report for the latest solution submission
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the coverage report
     */
    @GetMapping("programming-exercises/{exerciseId}/testwise-coverage-report")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<CoverageReport> getLatestCoverageReport(@PathVariable Long exerciseId) {
        log.debug("REST request to get the latest Testwise CoverageReport for exercise {}", exerciseId);

        var optionalReportWithoutFileReports = testwiseCoverageService.getCoverageReportForLatestSolutionSubmissionFromProgrammingExercise(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Coverage report for exercise " + exerciseId + " not found."));
        return ResponseEntity.ok(optionalReportWithoutFileReports);
    }
}
