package de.tum.in.www1.artemis.web.rest.hestia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.web.rest.dto.hestia.ProgrammingExerciseFullGitDiffReportDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ProgrammingExerciseGitDiffReports and its entries.
 */
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseGitDiffReportResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGitDiffReportResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseGitDiffReportService gitDiffReportService;

    private final ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository;

    public ProgrammingExerciseGitDiffReportResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseGitDiffReportService gitDiffReportService, ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository) {
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitDiffReportService = gitDiffReportService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
    }

    /**
     * {@code GET exercises/:exerciseId/diff-report} : Get the diff report for a programming exercise.
     *
     * @param exerciseId the exerciseId of the exercise of which to create the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the diff report
     */
    @GetMapping("programming-exercises/{exerciseId}/diff-report")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ProgrammingExerciseGitDiffReport> gitDiffReport(@PathVariable Long exerciseId) {
        log.debug("REST request to get a ProgrammingExerciseGitDiffReport for exercise {}", exerciseId);

        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var report = gitDiffReportService.getReportOfExercise(exercise);

        return ResponseEntity.ok(report);
    }

    /**
     * {@code GET exercises/:exerciseId/full-diff-report} : Create the full diff report for a programming exercise.
     * The fill diff report contains entries with the previous and current code blocks.
     *
     * @param exerciseId the exerciseId of the exercise of which to create the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the full diff report,
     */
    @GetMapping("programming-exercises/{exerciseId}/full-diff-report")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ProgrammingExerciseFullGitDiffReportDTO> getFullGitDiffReport(@PathVariable Long exerciseId) {
        log.debug("REST request to get a ProgrammingExerciseFullGitDiffReport for exercise {}", exerciseId);

        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var fullReport = gitDiffReportService.getFullReport(exercise);
        if (fullReport == null) {
            throw new EntityNotFoundException("No git diff report exists for this exercise");
        }

        return ResponseEntity.ok(fullReport);
    }
}
