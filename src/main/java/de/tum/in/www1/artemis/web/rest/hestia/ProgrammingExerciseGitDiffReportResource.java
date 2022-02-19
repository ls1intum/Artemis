package de.tum.in.www1.artemis.web.rest.hestia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * REST controller for managing ProgrammingExerciseGitDiffReports and its entries.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseGitDiffReportResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGitDiffReportResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseGitDiffReportService gitDiffReportService;

    public ProgrammingExerciseGitDiffReportResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseGitDiffReportService gitDiffReportService) {
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitDiffReportService = gitDiffReportService;
    }

    /**
     * {@code PUT  exercises/:exerciseId/diff-report} : Create a new exerciseHint for an exercise.
     *
     * @param exerciseId the exerciseId of the exercise of which to create the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new exerciseHint,
     * or with status {@code 400 (Bad Request)} if the exerciseHint is a codeHint,
     * or with status {@code 409 (Conflict)} if the exerciseId is invalid,
     */
    @PutMapping("programming-exercises/{exerciseId}/diff-report")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ProgrammingExerciseGitDiffReport> createExerciseHint(@PathVariable Long exerciseId) {
        log.debug("REST request to generate a ProgrammingExerciseGitDiffReport for exercise {}", exerciseId);

        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var report = gitDiffReportService.updateReportForExercise(exercise);
        if (report == null) {
            throw new InternalServerErrorException("Unable to generate diff report");
        }

        return ResponseEntity.ok(report);
    }
}
