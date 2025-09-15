package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.api.ExamApi;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.dto.RepoNameProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessLogDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseParticipationResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationResource.class);

    private static final String ENTITY_NAME = "programmingExerciseParticipation";

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingSubmissionService submissionService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final ResultService resultService;

    private final RepositoryService repositoryService;

    private final Optional<StudentExamApi> studentExamApi;

    private final Optional<VcsAccessLogRepository> vcsAccessLogRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final Optional<SharedQueueManagementService> sharedQueueManagementService;

    private final Optional<ExamApi> examApi;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    public ProgrammingExerciseParticipationResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ResultRepository resultRepository,
            ParticipationRepository participationRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionService submissionService, ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            ResultService resultService, ParticipationAuthorizationCheckService participationAuthCheckService, RepositoryService repositoryService,
            Optional<StudentExamApi> studentExamApi, Optional<VcsAccessLogRepository> vcsAccessLogRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            Optional<SharedQueueManagementService> sharedQueueManagementService, Optional<ExamApi> examApi,
            Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService) {
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.participationRepository = participationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.resultRepository = resultRepository;
        this.submissionService = submissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.resultService = resultService;
        this.participationAuthCheckService = participationAuthCheckService;
        this.repositoryService = repositoryService;
        this.studentExamApi = studentExamApi;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.vcsAccessLogRepository = vcsAccessLogRepository;
        this.sharedQueueManagementService = sharedQueueManagementService;
        this.examApi = examApi;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
    }

    /**
     * Get the given student participation with its latest result and feedbacks.
     *
     * @param participationId for which to retrieve the student participation with latest result and feedbacks.
     * @return the ResponseEntity with status 200 (OK) and the participation with its latest result in the body.
     */
    @GetMapping("programming-exercise-participations/{participationId}/student-participation-with-latest-result-and-feedbacks")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseStudentParticipation> getParticipationWithLatestResultForStudentParticipation(@PathVariable long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseParticipationService
                .findStudentParticipationWithLatestSubmissionResultAndFeedbacksElseThrow(participationId);
        hasAccessToParticipationElseThrow(participation);
        filterParticipationSubmissionResults(participation);
        // hide details that should not be shown to the students
        List<Result> results = participation.getSubmissions().isEmpty() ? List.of() : participation.getSubmissions().iterator().next().getResults();
        resultService.filterSensitiveInformationIfNecessary(participation, results, Optional.empty());
        return ResponseEntity.ok(participation);
    }

    private void filterParticipationSubmissionResults(ProgrammingExerciseStudentParticipation participation) {
        if (shouldHideExamExerciseResults(participation)) {
            participation.getSubmissions().forEach(submission -> submission.setResults(List.of()));
        }
    }

    /**
     * Get the given student participation with all results and feedbacks.
     *
     * @param participationId for which to retrieve the student participation with all results and feedbacks.
     * @return the ResponseEntity with status 200 (OK) and the participation with all results and feedbacks in the body.
     */
    // TODO: this is currently only used in the commit history view. Ideally we add an option to fetch this data additionally as part of the commit history endpoint below and
    // avoid sending so much data. Then, we can remove this endpoint in the future as well
    @GetMapping("programming-exercise-participations/{participationId}/student-participation-with-all-results")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseStudentParticipation> getParticipationWithAllResultsForStudentParticipation(@PathVariable Long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdWithAllResultsAndRelatedSubmissions(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Participation", participationId));

        // TODO: improve access checks to avoid fetching the user multiple times
        hasAccessToParticipationElseThrow(participation);
        filterParticipationSubmissionResults(participation);

        Set<Result> results = participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream().filter(Objects::nonNull)).collect(Collectors.toSet());
        // hide details that should not be shown to the students
        resultService.filterSensitiveInformationIfNecessary(participation, results, Optional.empty());
        return ResponseEntity.ok(participation);
    }

    /**
     * Get the student participation by its repository identifier.
     * The repository name is the last part of the repository URL.
     * The repository URL is built as follows: <code>{server.url}/git/{project_key}/{repo-name}.git</code> with <code>{repo-name}</code> consisting of
     * <code>{project-key}-{repo-type}</code>
     *
     * @param repoNameParam the repository identifier, e.g. {project_key}-{repo-name}
     * @return the ResponseEntity with status 200 (OK) and the participation DTO {@link de.tum.cit.aet.artemis.programming.dto.RepoNameProgrammingStudentParticipationDTO} in body,
     *         or with status 400 (Bad Request) if the repo name is not provided as request parameter,
     *         or with status 404 (Not Found) if the participation is not found,
     *         or with status 403 (Forbidden) if the user doesn't have access to the participation
     */
    @GetMapping("programming-exercise-participations")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<RepoNameProgrammingStudentParticipationDTO> getStudentParticipationByRepoName(@RequestParam(name = "repoName") String repoNameParam) {
        String repoUri;
        if (!StringUtils.hasText(repoNameParam)) {
            throw new BadRequestAlertException("Repository name must be provided", ENTITY_NAME, "repoNameRequired");
        }

        try {
            repoUri = new LocalVCRepositoryUri(localVCBaseUri, repoNameParam).toString();
        }
        catch (URISyntaxException e) {
            throw new BadRequestAlertException("Invalid repository URL", ENTITY_NAME, "invalidRepositoryUrl");
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("Invalid repository name", ENTITY_NAME, "invalidRepositoryName");
        }

        // find participation by url
        var participation = programmingExerciseStudentParticipationRepository.findByRepositoryUriElseThrow(repoUri);

        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        // check if the exercise is released. This also checks if the user can see an exam exercise
        if (!participation.getProgrammingExercise().isReleased()) {
            throw new AccessForbiddenException("exercise", participation.getProgrammingExercise().getId());
        }

        return ResponseEntity.ok(RepoNameProgrammingStudentParticipationDTO.of(participation));
    }

    /**
     * Get the latest result for a given programming exercise participation including its result.
     *
     * @param participationId for which to retrieve the programming exercise participation with latest result and feedbacks.
     * @param withSubmission  flag determining whether the corresponding submission should also be returned
     * @return the ResponseEntity with status 200 (OK) and the latest result with feedbacks in its body, 404 if the participation can't be found or 403 if the user is not allowed
     *         to access the participation.
     */
    @GetMapping("programming-exercise-participations/{participationId}/latest-result-with-feedbacks")
    @EnforceAtLeastStudent
    public ResponseEntity<Result> getLatestResultWithFeedbacksForProgrammingExerciseParticipation(@PathVariable Long participationId,
            @RequestParam(defaultValue = "false") boolean withSubmission) {
        var participation = participationRepository.findByIdElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        if (participation instanceof ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation
                && shouldHideExamExerciseResults(programmingExerciseStudentParticipation)) {
            return ResponseEntity.ok(null);
        }

        Optional<Result> result = resultRepository.findLatestResultWithFeedbacksForParticipation(participation.getId(), withSubmission);
        result.ifPresent(value -> resultService.filterSensitiveInformationIfNecessary(participation, value));

        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * Check if the participation has a result yet.
     *
     * @param participationId of the participation to check.
     * @return the ResponseEntity with status 200 (OK) with true if there is a result, otherwise false.
     */
    @GetMapping("programming-exercise-participations/{participationId}/has-result")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> checkIfParticipationHashResult(@PathVariable Long participationId) {
        boolean hasResult = resultRepository.existsBySubmissionParticipationId(participationId);
        return ResponseEntity.ok(hasResult);
    }

    /**
     * GET /programming-exercise-participation/:id/latest-pending-submission : get the latest pending submission for the participation.
     * A pending submission is one that does not have a result yet.
     *
     * @param participationId the id of the participation get the latest submission for
     * @param lastGraded      if true will not try to find the latest pending submission, but the latest GRADED pending submission.
     * @return the ResponseEntity with the last pending submission if it exists or null with status Ok (200). Will return notFound (404) if there is no participation for the given
     *         id and forbidden (403) if the user is not allowed to access the participation.
     */
    @GetMapping("programming-exercise-participations/{participationId}/latest-pending-submission")
    @EnforceAtLeastStudent
    public ResponseEntity<SubmissionDTO> getLatestPendingSubmission(@PathVariable Long participationId, @RequestParam(defaultValue = "false") boolean lastGraded) {
        Optional<ProgrammingSubmission> submissionOpt;
        try {
            submissionOpt = submissionService.getLatestPendingSubmission(participationId, lastGraded);
        }
        catch (IllegalArgumentException ex) {
            throw new EntityNotFoundException("participation", participationId);
        }
        if (submissionOpt.isEmpty()) {
            return ResponseEntity.ok(null);
        }
        ProgrammingSubmission programmingSubmission = submissionOpt.get();
        boolean isSubmissionProcessing = false;
        ZonedDateTime buildStartDate = null;
        ZonedDateTime estimatedCompletionDate = null;
        if (sharedQueueManagementService.isPresent()) {
            try {
                var buildTimingInfo = sharedQueueManagementService.get().isSubmissionProcessing(participationId, programmingSubmission.getCommitHash());
                if (buildTimingInfo != null) {
                    isSubmissionProcessing = true;
                    buildStartDate = buildTimingInfo.buildStartDate();
                    estimatedCompletionDate = buildTimingInfo.estimatedCompletionDate();
                }
            }
            catch (Exception e) {
                log.warn("Failed to get build timing info for submission {} of participation {}: {}", programmingSubmission.getCommitHash(), participationId, e.getMessage());
            }
        }

        // Remove participation, is not needed in the response.
        programmingSubmission.setParticipation(null);
        var submissionDTO = SubmissionDTO.of(programmingSubmission, isSubmissionProcessing, buildStartDate, estimatedCompletionDate);
        return ResponseEntity.ok(submissionDTO);
    }

    /**
     * For every student participation of a programming exercise, try to find a pending submission.
     *
     * @param exerciseId for which to search pending submissions.
     * @return a Map of {[participationId]: ProgrammingSubmission | null}. Will contain an entry for every student participation of the exercise and a submission object if a
     *         pending submission exists or null if not.
     */
    @GetMapping("programming-exercises/{exerciseId}/latest-pending-submissions")
    @EnforceAtLeastTutor
    public ResponseEntity<Map<Long, Optional<Submission>>> getLatestPendingSubmissionsByExerciseId(@PathVariable Long exerciseId) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            throw new AccessForbiddenException("exercise", exerciseId);
        }
        // TODO: use a different data structure than map here
        Map<Long, Optional<Submission>> pendingSubmissions = submissionService.getLatestPendingSubmissionsForProgrammingExercise(exerciseId);
        // Remove unnecessary data to make response smaller (exercise, student of participation).
        pendingSubmissions = pendingSubmissions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            Optional<Submission> submissionOpt = entry.getValue();
            // Remove participation, is not needed in the response.
            submissionOpt.ifPresent(submission -> submission.setParticipation(null));
            return submissionOpt;
        }));
        return ResponseEntity.ok(pendingSubmissions);
    }

    /**
     * Resets the specified repository to either the exercise template or graded participation
     *
     * @param participationId       the id of the programming participation that should be resetted
     * @param gradedParticipationId optional parameter that specifies that the repository should be set to the graded participation instead of the exercise template
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("programming-exercise-participations/{participationId}/reset-repository")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> resetRepository(@PathVariable Long participationId, @RequestParam(required = false) Long gradedParticipationId)
            throws GitAPIException, IOException {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByStudentParticipationIdWithTemplateParticipation(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise for Participation", participationId));
        participation.setProgrammingExercise(exercise);

        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        if (participationAuthCheckService.isLocked(participation, exercise)) {
            throw new AccessForbiddenException("participation", participationId);
        }
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Cannot reset repository in an exam", ENTITY_NAME, "noRepoResetInExam");
        }

        LocalVCRepositoryUri sourceUri;
        if (gradedParticipationId != null) {
            ProgrammingExerciseStudentParticipation gradedParticipation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(gradedParticipationId);
            participationAuthCheckService.checkCanAccessParticipationElseThrow(gradedParticipation);

            sourceUri = gradedParticipation.getVcsRepositoryUri();
        }
        else {
            sourceUri = exercise.getVcsTemplateRepositoryUri();
        }

        programmingExerciseParticipationService.resetRepository(participation.getVcsRepositoryUri(), sourceUri);
        continuousIntegrationTriggerService
                .orElseThrow(() -> new UnsupportedOperationException(
                        "Cannot trigger build because neither the Jenkins nor the LocalCI profile are active. This is a misconfiguration if you want to use programming exercises"))
                .triggerBuild(participation, true);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /programming-exercise-participations/{participationId}/commits-history : Get the commit history of a programming exercise participation.
     * Here we check is at least a teaching assistant for the exercise. If true the user can have access to the commit history of any participation of the exercise.
     * If the user is a student, we check if the user is the owner of the participation.
     *
     * @param participationId the id of the participation for which to retrieve the commit history
     * @return the ResponseEntity with status 200 (OK) and with body a list of commitInfo DTOs with the commits information of the participation
     */
    @GetMapping("programming-exercise-participations/{participationId}/commit-history")
    @EnforceAtLeastStudent
    // TODO: we should change this to use a paging mechanism in the future, as this can return a lot of data
    // TODO: allow to add result information, i.e. fetch the corresponding submissions (based on the commit hashes) and their results from the database
    // check if the user has access and then add the result information to the commit info DTOs so the client can display them grouped by pushed commit
    // each pushed commit should have a submission with result in the database
    public ResponseEntity<List<CommitInfoDTO>> getCommitHistoryForParticipationRepo(@PathVariable long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        var commitInfo = programmingExerciseParticipationService.getCommitInfos(participation.getVcsRepositoryUri());
        return ResponseEntity.ok(commitInfo);
    }

    /**
     * GET /programming-exercise-participations/{participationId}/vcs-access-log :
     * Here we check if the user is least an instructor for the exercise. If true the user can have access to the vcs access log of any participation of the exercise.
     *
     * @param participationId the id of the participation for which to retrieve the vcs access log
     * @return the ResponseEntity with status 200 (OK) and with body containing a list of vcsAccessLogDTOs of the participation, or 400 (Bad request) if localVC is not enabled.
     */
    @GetMapping("programming-exercise-participations/{participationId}/vcs-access-log")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<VcsAccessLogDTO>> getVcsAccessLogForParticipationRepo(@PathVariable long participationId) {
        if (vcsAccessLogRepository.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        log.info("Fetching VCS access logs for participation ID: {}", participationId);
        List<VcsAccessLog> vcsAccessLogs = vcsAccessLogRepository.get().findAllByParticipationId(participationId);
        var vcsAccessLogDTOs = vcsAccessLogs.stream().map(VcsAccessLogDTO::of).toList();
        return ResponseEntity.ok(vcsAccessLogDTOs);
    }

    /**
     * GET /programming-exercise/{exerciseId}/commit-history/{repositoryType} : Get the commit history of a programming exercise repository. The repository type can be TEMPLATE or
     * SOLUTION, TESTS or AUXILIARY.
     * Here we check is at least a teaching assistant for the exercise.
     *
     * @param exerciseId     the id of the exercise for which to retrieve the commit history
     * @param repositoryType the type of the repository for which to retrieve the commit history
     * @param repositoryId   the id of the repository
     * @return the ResponseEntity with status 200 (OK) and with body a list of commitInfo DTOs with the commits information of the repository
     */
    @GetMapping("programming-exercise/{exerciseId}/commit-history/{repositoryType}")
    @EnforceAtLeastTutor
    public ResponseEntity<List<CommitInfoDTO>> getCommitHistoryForTemplateSolutionTestOrAuxRepo(@PathVariable long exerciseId, @PathVariable RepositoryType repositoryType,
            @RequestParam Optional<Long> repositoryId) {
        boolean isTemplateRepository = repositoryType.equals(RepositoryType.TEMPLATE);
        boolean isSolutionRepository = repositoryType.equals(RepositoryType.SOLUTION);
        boolean isTestRepository = repositoryType.equals(RepositoryType.TESTS);
        boolean isAuxiliaryRepository = repositoryType.equals(RepositoryType.AUXILIARY);
        ProgrammingExerciseParticipation participation;

        if (!isTemplateRepository && !isSolutionRepository && !isTestRepository && !isAuxiliaryRepository) {
            throw new BadRequestAlertException("Invalid repository type", ENTITY_NAME, "invalidRepositoryType");
        }
        else if (isTemplateRepository) {
            participation = programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(exerciseId);
        }
        else {
            participation = programmingExerciseParticipationService.findSolutionParticipationByProgrammingExerciseId(exerciseId);
        }
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        if (isAuxiliaryRepository) {
            var auxiliaryRepo = auxiliaryRepositoryRepository.findByIdElseThrow(repositoryId.orElseThrow());
            if (!auxiliaryRepo.getExercise().getId().equals(exerciseId)) {
                throw new BadRequestAlertException("Invalid repository id", ENTITY_NAME, "invalidRepositoryId");
            }
            return ResponseEntity.ok(programmingExerciseParticipationService.getCommitInfos(auxiliaryRepo.getVcsRepositoryUri()));
        }
        else if (isTestRepository) {
            return ResponseEntity.ok(programmingExerciseParticipationService.getCommitInfos(participation.getProgrammingExercise().getVcsTestRepositoryUri()));
        }
        else if (isTemplateRepository) {
            return ResponseEntity.ok(programmingExerciseParticipationService.getCommitInfos(participation.getProgrammingExercise().getVcsTemplateRepositoryUri()));
        }
        else {
            return ResponseEntity.ok(programmingExerciseParticipationService.getCommitInfos(participation.getProgrammingExercise().getVcsSolutionRepositoryUri()));
        }
    }

    /**
     * GET /programming-exercise-participations/{participationId}/files-content : Get the content of the files of a programming exercise participation.
     *
     * @param participationId the id of the participation for which to retrieve the files content
     * @param commitId        the id of the commit for which to retrieve the files content
     * @return The files of repository along with their content
     */
    @GetMapping("programming-exercise-participations/{participationId}/files-content/{commitId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Map<String, String>> getParticipationRepositoryFiles(@PathVariable long participationId, @PathVariable String commitId) throws IOException {
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        ProgrammingExercise exercise = programmingExerciseRepository.getProgrammingExerciseFromParticipationElseThrow(participation);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        return ResponseEntity.ok(repositoryService.getFilesContentAtCommit(exercise, commitId, null, participation));
    }

    /**
     * GET /programming-exercise/{exerciseId}/files-content-commit-details/{commitId} : Get the content of the files of a programming exercise
     * This method is specifically for the commit details view, where not only Instructors and Admins should have access to the files content as in
     * getParticipationRepositoryFiles but also students and tutors that have access to the participation.
     *
     * @param exerciseId      the id of the exercise for which to retrieve the files content
     * @param participationId the id of the participation for which to retrieve the files content
     * @param commitId        the id of the commit for which to retrieve the files content
     * @param repositoryType  the type of the repository for which to retrieve the files content
     * @return The files of the repository along with their content
     */
    @GetMapping("programming-exercise/{exerciseId}/files-content-commit-details/{commitId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, String>> getParticipationRepositoryFilesForCommitsDetailsView(@PathVariable long exerciseId, @PathVariable String commitId,
            @RequestParam(required = false) Long participationId, @RequestParam(required = false) RepositoryType repositoryType) throws IOException {
        if (participationId != null) {
            Participation participation = participationRepository.findByIdElseThrow(participationId);
            ProgrammingExerciseParticipation programmingExerciseParticipation = repositoryService.getAsProgrammingExerciseParticipationOfExerciseElseThrow(exerciseId,
                    participation, ENTITY_NAME);
            ProgrammingExercise programmingExercise = programmingExerciseRepository.getProgrammingExerciseFromParticipation(programmingExerciseParticipation);
            participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
            // we only forward the repository type for the test repository, as the test repository is the only one that needs to be treated differently
            return ResponseEntity.ok(repositoryService.getFilesContentAtCommit(programmingExercise, commitId, null, programmingExerciseParticipation));
        }
        else if (repositoryType != null) {
            ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
            var participation = repositoryType == RepositoryType.TEMPLATE ? programmingExercise.getTemplateParticipation() : programmingExercise.getSolutionParticipation();
            return ResponseEntity.ok(repositoryService.getFilesContentAtCommit(programmingExercise, commitId, repositoryType, participation));
        }
        else {
            throw new BadRequestAlertException("Either participationId or repositoryType must be provided", ENTITY_NAME, "missingParameters");
        }
    }

    /**
     * Retrieves the VCS access logs for the specified programming exercise's template or solution participation
     *
     * @param exerciseId     the ID of the programming exercise
     * @param repositoryType the type of repository (either TEMPLATE or SOLUTION) for which to retrieve the logs.
     * @return the ResponseEntity with status 200 (OK) and with body containing a list of vcsAccessLogDTOs of the participation, or 400 (Bad request) if localVC is not enabled.
     * @throws BadRequestAlertException if the repository type is invalid
     */
    @GetMapping("programming-exercise/{exerciseId}/vcs-access-log/{repositoryType}")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<List<VcsAccessLogDTO>> getVcsAccessLogForExerciseRepository(@PathVariable long exerciseId, @PathVariable RepositoryType repositoryType) {
        if (vcsAccessLogRepository.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (repositoryType != RepositoryType.TEMPLATE && repositoryType != RepositoryType.SOLUTION) {
            throw new BadRequestAlertException("Can only get vcs access log from template and assignment repositories", ENTITY_NAME, "incorrect repositoryType");
        }
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);
        log.info("Fetching VCS access logs for exercise ID: {} and repository type: {}", exerciseId, repositoryType);

        var participation = repositoryType == RepositoryType.TEMPLATE ? programmingExercise.getTemplateParticipation() : programmingExercise.getSolutionParticipation();

        List<VcsAccessLog> vcsAccessLogs = vcsAccessLogRepository.get().findAllByParticipationId(participation.getId());
        var vcsAccessLogDTOs = vcsAccessLogs.stream().map(VcsAccessLogDTO::of).toList();
        return ResponseEntity.ok(vcsAccessLogDTOs);
    }

    /**
     * Checks if the user has access to the participation.
     * If the exercise has not started yet and the user is a student, access is denied.
     *
     * @param participation the participation to check
     */
    private void hasAccessToParticipationElseThrow(ProgrammingExerciseStudentParticipation participation) {
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        ZonedDateTime exerciseStartDate = participation.getExercise().getParticipationStartDate();
        if (exerciseStartDate != null) {
            boolean isStudent = authCheckService.isOnlyStudentInCourse(participation.getExercise().getCourseViaExerciseGroupOrCourseMember(), null);
            boolean exerciseNotStarted = exerciseStartDate.isAfter(ZonedDateTime.now());
            if (isStudent && exerciseNotStarted) {
                throw new AccessForbiddenException("Participation not yet started");
            }
        }
    }

    /**
     * Checks if the results should be hidden for the given participation.
     *
     * @param participation the participation to check
     * @return true if the results should be hidden, false otherwise
     */
    private boolean shouldHideExamExerciseResults(ProgrammingExerciseStudentParticipation participation) {
        if (participation.getProgrammingExercise().isExamExercise() && !participation.getProgrammingExercise().isTestExamExercise()) {
            var examApi = this.examApi.orElseThrow(() -> new ExamApiNotPresentException(ExamApi.class));
            var studentExamApi = this.studentExamApi.orElseThrow(() -> new ExamApiNotPresentException(StudentExamApi.class));
            User student = participation.getStudent()
                    .orElseThrow(() -> new EntityNotFoundException("Participation with id " + participation.getId() + " does not have a student!"));
            var studentExam = studentExamApi.findByExerciseIdAndUserId(participation.getExercise().getId(), student.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Participation " + participation.getId() + " does not have a student exam!"));
            return !examApi.shouldStudentSeeResult(studentExam, participation);
        }
        return false;
    }

}
