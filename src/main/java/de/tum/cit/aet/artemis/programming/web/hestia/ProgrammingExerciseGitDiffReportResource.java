package de.tum.cit.aet.artemis.programming.web.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.CommitHistoryService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.cit.aet.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.web.rest.dto.ProgrammingExerciseGitDiffReportDTO;

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

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseGitDiffReportService gitDiffReportService;

    private final ProgrammingSubmissionRepository submissionRepository;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final CommitHistoryService commitHistoryService;

    private final RepositoryService repositoryService;

    private static final String ENTITY_NAME = "programmingExerciseGitDiffReportEntry";

    public ProgrammingExerciseGitDiffReportResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            ParticipationRepository participationRepository, ProgrammingExerciseGitDiffReportService gitDiffReportService, ProgrammingSubmissionRepository submissionRepository,
            ParticipationAuthorizationCheckService participationAuthCheckService, CommitHistoryService commitHistoryService, RepositoryService repositoryService) {
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationRepository = participationRepository;
        this.gitDiffReportService = gitDiffReportService;
        this.submissionRepository = submissionRepository;
        this.participationAuthCheckService = participationAuthCheckService;
        this.commitHistoryService = commitHistoryService;
        this.repositoryService = repositoryService;
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
     * GET exercises/:exerciseId/participation/:participationId/commits/:commitHash1/diff-report/:commitHash2 :
     * Get the diff report for two commits of a programming exercise.
     * If the repositoryType is USER, the current user needs to have at least student access to the participation to fetch the diff report for the commits.
     * If the repositoryType is TEMPLATE, SOLUTION or TESTS, the current user needs to have at least instructor access to the exercise to fetch the diff report for the commits.
     *
     * @param exerciseId      the id of the exercise the two commits belong to
     * @param participationId the id of the participation the two commits belong to
     * @param commitHash1     the hash of the first (older) commit
     * @param commitHash2     the hash of the second (newer) commit
     * @param repositoryType  the type of the repository to fetch the diff report for
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with the diff report as body
     * @throws GitAPIException if errors occur while accessing the git repository
     * @throws IOException     if errors occur while accessing the file system
     */
    @GetMapping("programming-exercises/{exerciseId}/commits/{commitHash1}/diff-report/{commitHash2}")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseGitDiffReportDTO> getGitDiffReportForCommits(@PathVariable long exerciseId, @PathVariable String commitHash1,
            @PathVariable String commitHash2, @RequestParam(required = false) Long participationId, @RequestParam(required = false) RepositoryType repositoryType)
            throws GitAPIException, IOException {
        log.debug("REST request to get a diff report for two commits for commit {} and commit {} of participation {}", commitHash1, commitHash2, participationId);

        VcsRepositoryUri repositoryUri = null;
        if (participationId != null) {
            Participation participation = participationRepository.findByIdElseThrow(participationId);
            participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
            var programmingExerciseParticipation = repositoryService.getAsProgrammingExerciseParticipationOfExerciseElseThrow(exerciseId, participation, ENTITY_NAME);
            repositoryUri = programmingExerciseParticipation.getVcsRepositoryUri();
        }
        else if (repositoryType != null) {
            ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);
            repositoryUri = switch (repositoryType) {
                case TEMPLATE -> programmingExercise.getTemplateParticipation().getVcsRepositoryUri();
                case SOLUTION -> programmingExercise.getSolutionParticipation().getVcsRepositoryUri();
                case TESTS -> programmingExercise.getVcsTestRepositoryUri();
                default -> throw new BadRequestAlertException("Invalid repository type", ENTITY_NAME, "invalidRepositoryType");
            };
        }
        else {
            throw new BadRequestAlertException("Either participationId or repositoryType must be provided", ENTITY_NAME, "missingParameters");
        }
        var report = commitHistoryService.generateReportForCommits(repositoryUri, commitHash1, commitHash2);
        return ResponseEntity.ok(new ProgrammingExerciseGitDiffReportDTO(report));
    }
}
