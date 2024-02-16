package de.tum.in.www1.artemis.web.rest.hestia;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseGitDiffReportDTO;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing ProgrammingExerciseGitDiffReports and its entries.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseGitDiffReportResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGitDiffReportResource.class);

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseGitDiffReportService gitDiffReportService;

    private final ProgrammingSubmissionRepository submissionRepository;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private static final String ENTITY_NAME = "programmingExerciseGitDiffReportEntry";

    public ProgrammingExerciseGitDiffReportResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseGitDiffReportService gitDiffReportService, ProgrammingSubmissionRepository submissionRepository,
            ParticipationAuthorizationCheckService participationAuthCheckService) {
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitDiffReportService = gitDiffReportService;
        this.submissionRepository = submissionRepository;
        this.participationAuthCheckService = participationAuthCheckService;
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
     * The current user needs to have at least instructor access to the exercise to fetch the diff report for the submissions.
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
        var submission1 = submissionRepository.findById(submissionId1).orElseThrow();
        var submission2 = submissionRepository.findById(submissionId2).orElseThrow();
        // If either of the two submissions does not belong to the exercise, throw an exception because we do not want to support this for now and while the git diff calucation
        // would support that,
        // it would lead to confusing results displayed in the client because the client interface hasn't been designed for this use case.
        if (!submission1.getParticipation().getExercise().getId().equals(exerciseId) || !submission2.getParticipation().getExercise().getId().equals(exerciseId)) {
            throw new IllegalArgumentException("The submissions do not belong to the exercise");
        }
        var report = gitDiffReportService.generateReportForSubmissions(submission1, submission2);
        return ResponseEntity.ok(new ProgrammingExerciseGitDiffReportDTO(report));
    }

    /**
     * GET exercises/:exerciseId/submissions/:submissionId1/diff-report-with-template : Get the diff report for a submission of a programming exercise with the template of the
     * exercise.
     * The current user needs to have at least instructor access to the exercise to fetch the diff report with the template.
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
        var submission = submissionRepository.findById(submissionId1).orElseThrow();
        if (!submission.getParticipation().getExercise().getId().equals(exerciseId)) {
            throw new IllegalArgumentException("The submission does not belong to the exercise");
        }
        var report = gitDiffReportService.createReportForSubmissionWithTemplate(exercise, submission);
        return ResponseEntity.ok(new ProgrammingExerciseGitDiffReportDTO(report));
    }

    /**
     * GET exercises/:exerciseId/submissions/:submissionId1/diff-report-commit-details/:submissionId2 : Get the diff report for two submissions of a programming exercise.
     * To fetch the diff report for the commit details view, the current user needs to have access to the participation of the submissions.
     *
     * @param exerciseId    the id of the exercise the two submissions belong to
     * @param submissionId1 the id of the first (older) submission
     * @param submissionId2 the id of the second (newer) submission
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with the diff report as body
     * @throws GitAPIException if errors occur while accessing the git repository
     * @throws IOException     if errors occur while accessing the file system
     */
    @GetMapping("programming-exercises/{exerciseId}/submissions/{submissionId1}/diff-report-commit-details/{submissionId2}")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseGitDiffReportDTO> getGitDiffReportForCommitDetailsViewForSubmissions(@PathVariable long exerciseId, @PathVariable long submissionId1,
            @PathVariable long submissionId2) throws GitAPIException, IOException {
        log.debug("REST request to get a ProgrammingExerciseGitDiffReport for the commit details view for submission {} and submission {} of exercise {}", submissionId1,
                submissionId2, exerciseId);
        var submission1 = submissionRepository.findById(submissionId1).orElseThrow();
        var submission2 = submissionRepository.findById(submissionId2).orElseThrow();
        // If either of the two submissions does not belong to the exercise, throw an exception because we do not want to support this for now and while the git diff calucation
        // would support that,
        // it would lead to confusing results displayed in the client because the client interface hasn't been designed for this use case.
        if (!submission1.getParticipation().getExercise().getId().equals(exerciseId) || !submission2.getParticipation().getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("A git diff report entry can only be retrieved if the submissions exercise id match with the exercise id", ENTITY_NAME,
                    "exerciseIdsMismatch");
        }
        participationAuthCheckService.checkCanAccessParticipationElseThrow(submission1.getParticipation());
        participationAuthCheckService.checkCanAccessParticipationElseThrow(submission2.getParticipation());
        var report = gitDiffReportService.generateReportForSubmissions(submission1, submission2);
        return ResponseEntity.ok(new ProgrammingExerciseGitDiffReportDTO(report));
    }

    /**
     * GET exercises/:exerciseId/submissions/:submissionId1/diff-report-commit-details-with-template : Get the diff report for a submission of a programming exercise with the
     * template of the exercise. To fetch the diff report for the commit details view, the current user needs to have access to the participation of the submission.
     *
     * @param exerciseId    the id of the exercise the submission and the template belong to
     * @param submissionId1 the id of the submission
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with the diff report as body
     * @throws GitAPIException if errors occur while accessing the git repository
     * @throws IOException     if errors occur while accessing the file system
     */
    @GetMapping("programming-exercises/{exerciseId}/submissions/{submissionId1}/diff-report-commit-details-with-template")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseGitDiffReportDTO> getGitDiffReportForCommitDetailsViewForSubmissionWithTemplate(@PathVariable long exerciseId,
            @PathVariable long submissionId1) throws GitAPIException, IOException {
        log.debug("REST request to get a ProgrammingExerciseGitDiffReport for the commit details view for submission {} with the template of exercise {}", submissionId1,
                exerciseId);
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var submission = submissionRepository.findById(submissionId1).orElseThrow();
        if (!submission.getParticipation().getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("A git diff report entry can only be retrieved if the submissions exercise id matches with the exercise id", ENTITY_NAME,
                    "exerciseIdsMismatch");
        }
        participationAuthCheckService.checkCanAccessParticipationElseThrow(submission.getParticipation());
        var report = gitDiffReportService.createReportForSubmissionWithTemplate(exercise, submission);
        return ResponseEntity.ok(new ProgrammingExerciseGitDiffReportDTO(report));
    }
}
