package de.tum.in.www1.artemis.web.rest.hestia;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseGitDiffReportDTO;

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

    public ProgrammingExerciseGitDiffReportResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseGitDiffReportService gitDiffReportService) {
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitDiffReportService = gitDiffReportService;
    }

    /**
     * {@code GET exercises/:exerciseId/diff-report} : Get the diff report for a programming exercise.
     *
     * @param exerciseId the exerciseId of the exercise of which to create the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the diff report
     */
    @GetMapping("programming-exercises/{exerciseId}/diff-report")
    @EnforceAtLeastTutor
    public ResponseEntity<ProgrammingExerciseGitDiffReport> getGitDiffReport(@PathVariable Long exerciseId) {
        log.debug("REST request to get a ProgrammingExerciseGitDiffReport for exercise {}", exerciseId);

        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        var report = gitDiffReportService.getOrCreateReportOfExercise(exercise);

        return ResponseEntity.ok(report);
    }

    /**
     * GET exercises/:exerciseId/submissions/:submissionId1/diff-report/:submissionId2 : Get the diff report for two submissions of a programming exercise.
     *
     * @param exerciseId    the id of the exercise the two submissions belong to
     * @param submissionId1 the id of the first (older) submission
     * @param submissionId2 the id of the second (newer) submission
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with the diff report as body
     * @throws GitAPIException if errors occur while accessing the git repository
     * @throws IOException     if errors occur while accessing the file system
     */
    @GetMapping("programming-exercises/{exerciseId}/submissions/{submissionId1}/diff-report/{submissionId2}")
    @EnforceAtLeastInstructor
    public ResponseEntity<ProgrammingExerciseGitDiffReportDTO> getGitDiffReportForSubmissions(@PathVariable long exerciseId, @PathVariable long submissionId1,
            @PathVariable long submissionId2) throws GitAPIException, IOException {
        log.debug("REST request to get a ProgrammingExerciseGitDiffReport for submission {} and submission {} of exercise {}", submissionId1, submissionId2, exerciseId);
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        var report = gitDiffReportService.createReportForSubmissions(submissionId1, submissionId2);
        return ResponseEntity.ok(ProgrammingExerciseGitDiffReportDTO.of(report));
    }

    /**
     * GET exercises/:exerciseId/submissions/:submissionId1/diff-report-with-template : Get the diff report for a submission of a programming exercise with the template of the
     * exercise.
     *
     * @param exerciseId    the id of the exercise the submission and the template belong to
     * @param submissionId1 the id of the submission
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with the diff report as body
     * @throws GitAPIException if errors occur while accessing the git repository
     * @throws IOException     if errors occur while accessing the file system
     */
    @GetMapping("programming-exercises/{exerciseId}/submissions/{submissionId1}/diff-report-with-template")
    @EnforceAtLeastInstructor
    public ResponseEntity<ProgrammingExerciseGitDiffReportDTO> getGitDiffReportForSubmissionWithTemplate(@PathVariable long exerciseId, @PathVariable long submissionId1)
            throws GitAPIException, IOException {
        log.debug("REST request to get a ProgrammingExerciseGitDiffReport for submission {} with the template of exercise {}", submissionId1, exerciseId);
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        var report = gitDiffReportService.createReportForSubmissionWithTemplate(exercise, submissionId1);
        return ResponseEntity.ok(ProgrammingExerciseGitDiffReportDTO.of(report));
    }
}
