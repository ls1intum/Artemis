package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
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
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessLogDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseParticipationResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationResource.class);

    private static final String ENTITY_NAME = "programmingExerciseParticipation";

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

    private final StudentExamRepository studentExamRepository;

    private final Optional<VcsAccessLogRepository> vcsAccessLogRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    public ProgrammingExerciseParticipationResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ResultRepository resultRepository,
            ParticipationRepository participationRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionService submissionService, ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            ResultService resultService, ParticipationAuthorizationCheckService participationAuthCheckService, RepositoryService repositoryService,
            StudentExamRepository studentExamRepository, Optional<VcsAccessLogRepository> vcsAccessLogRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository) {
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
        this.studentExamRepository = studentExamRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.vcsAccessLogRepository = vcsAccessLogRepository;
    }

    /**
     * Get the given student participation with its latest result and feedbacks.
     *
     * @param participationId for which to retrieve the student participation with latest result and feedbacks.
     * @return the ResponseEntity with status 200 (OK) and the participation with its latest result in the body.
     */
    @GetMapping("programming-exercise-participations/{participationId}/student-participation-with-latest-result-and-feedbacks")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseStudentParticipation> getParticipationWithLatestResultForStudentParticipation(@PathVariable Long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository
                .findStudentParticipationWithLatestResultAndFeedbacksAndRelatedSubmissions(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Participation", participationId));

        hasAccessToParticipationElseThrow(participation);
        if (shouldHideExamExerciseResults(participation)) {
            participation.setResults(Set.of());
        }

        // hide details that should not be shown to the students
        resultService.filterSensitiveInformationIfNecessary(participation, participation.getResults(), Optional.empty());
        return ResponseEntity.ok(participation);
    }

    /**
     * Get the given student participation with all results and feedbacks.
     *
     * @param participationId for which to retrieve the student participation with all results and feedbacks.
     * @return the ResponseEntity with status 200 (OK) and the participation with all results and feedbacks in the body.
     */
    @GetMapping("programming-exercise-participations/{participationId}/student-participation-with-all-results")
    @EnforceAtLeastStudent
    public ResponseEntity<ProgrammingExerciseStudentParticipation> getParticipationWithAllResultsForStudentParticipation(@PathVariable Long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdWithAllResultsAndRelatedSubmissions(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Participation", participationId));

        // TODO: improve access checks to avoid fetching the user multiple times
        hasAccessToParticipationElseThrow(participation);
        if (shouldHideExamExerciseResults(participation)) {
            participation.setResults(Set.of());
        }

        // hide details that should not be shown to the students
        resultService.filterSensitiveInformationIfNecessary(participation, participation.getResults(), Optional.empty());
        return ResponseEntity.ok(participation);
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
        boolean hasResult = resultRepository.existsByParticipationId(participationId);
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
    public ResponseEntity<ProgrammingSubmission> getLatestPendingSubmission(@PathVariable Long participationId, @RequestParam(defaultValue = "false") boolean lastGraded) {
        Optional<ProgrammingSubmission> submissionOpt;
        try {
            submissionOpt = submissionService.getLatestPendingSubmission(participationId, lastGraded);
        }
        catch (IllegalArgumentException ex) {
            throw new EntityNotFoundException("participation", participationId);
        }
        // Remove participation, is not needed in the response.
        submissionOpt.ifPresent(submission -> submission.setParticipation(null));
        return ResponseEntity.ok(submissionOpt.orElse(null));
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
    public ResponseEntity<Map<Long, Optional<ProgrammingSubmission>>> getLatestPendingSubmissionsByExerciseId(@PathVariable Long exerciseId) {
        ProgrammingExercise programmingExercise;
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            throw new AccessForbiddenException("exercise", exerciseId);
        }
        Map<Long, Optional<ProgrammingSubmission>> pendingSubmissions = submissionService.getLatestPendingSubmissionsForProgrammingExercise(exerciseId);
        // Remove unnecessary data to make response smaller (exercise, student of participation).
        pendingSubmissions = pendingSubmissions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            Optional<ProgrammingSubmission> submissionOpt = entry.getValue();
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
        if (participation.isLocked()) {
            throw new AccessForbiddenException("participation", participationId);
        }
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Cannot reset repository in an exam", ENTITY_NAME, "noRepoResetInExam");
        }

        VcsRepositoryUri sourceURL;
        if (gradedParticipationId != null) {
            ProgrammingExerciseStudentParticipation gradedParticipation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(gradedParticipationId);
            participationAuthCheckService.checkCanAccessParticipationElseThrow(gradedParticipation);

            sourceURL = gradedParticipation.getVcsRepositoryUri();
        }
        else {
            sourceURL = exercise.getVcsTemplateRepositoryUri();
        }

        programmingExerciseParticipationService.resetRepository(participation.getVcsRepositoryUri(), sourceURL);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /programming-exercise-participations/{participationId}/commits-info : Get the commits information (author, timestamp, message, hash) of a programming exercise
     * participation.
     *
     * @param participationId the id of the participation for which to retrieve the commits information
     * @return A list of commitInfo DTOs with the commits information of the participation
     */
    @GetMapping("programming-exercise-participations/{participationId}/commits-info")
    @EnforceAtLeastInstructor
    List<CommitInfoDTO> getCommitInfosForParticipationRepo(@PathVariable long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, participation.getProgrammingExercise(), null);
        return programmingExerciseParticipationService.getCommitInfos(participation);
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
    public ResponseEntity<List<CommitInfoDTO>> getCommitHistoryForParticipationRepo(@PathVariable long participationId) {
        ProgrammingExerciseStudentParticipation participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        var commitInfo = programmingExerciseParticipationService.getCommitInfos(participation);
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
     * GET /programming-exercise/{exerciseID}/commit-history/{repositoryType} : Get the commit history of a programming exercise repository. The repository type can be TEMPLATE or
     * SOLUTION, TESTS or AUXILIARY.
     * Here we check is at least a teaching assistant for the exercise.
     *
     * @param exerciseID     the id of the exercise for which to retrieve the commit history
     * @param repositoryType the type of the repository for which to retrieve the commit history
     * @param repositoryId   the id of the repository
     * @return the ResponseEntity with status 200 (OK) and with body a list of commitInfo DTOs with the commits information of the repository
     */
    @GetMapping("programming-exercise/{exerciseID}/commit-history/{repositoryType}")
    @EnforceAtLeastTutor
    public ResponseEntity<List<CommitInfoDTO>> getCommitHistoryForTemplateSolutionTestOrAuxRepo(@PathVariable long exerciseID, @PathVariable RepositoryType repositoryType,
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
            participation = programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(exerciseID);
        }
        else {
            participation = programmingExerciseParticipationService.findSolutionParticipationByProgrammingExerciseId(exerciseID);
        }
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        if (isAuxiliaryRepository) {
            var auxiliaryRepo = auxiliaryRepositoryRepository.findByIdElseThrow(repositoryId.orElseThrow());
            if (!auxiliaryRepo.getExercise().getId().equals(exerciseID)) {
                throw new BadRequestAlertException("Invalid repository id", ENTITY_NAME, "invalidRepositoryId");
            }
            return ResponseEntity.ok(programmingExerciseParticipationService.getAuxiliaryRepositoryCommitInfos(auxiliaryRepo));
        }

        if (isTestRepository) {
            return ResponseEntity.ok(programmingExerciseParticipationService.getCommitInfosTestRepo(participation));
        }
        else {
            return ResponseEntity.ok(programmingExerciseParticipationService.getCommitInfos(participation));
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
    public ResponseEntity<Map<String, String>> getParticipationRepositoryFiles(@PathVariable long participationId, @PathVariable String commitId)
            throws GitAPIException, IOException {
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
            @RequestParam(required = false) Long participationId, @RequestParam(required = false) RepositoryType repositoryType) throws GitAPIException, IOException {
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
        if (participation.getProgrammingExercise().isExamExercise()) {
            User student = participation.getStudent()
                    .orElseThrow(() -> new EntityNotFoundException("Participation with id " + participation.getId() + " does not have a student!"));
            var studentExam = studentExamRepository.findByExerciseIdAndUserId(participation.getExercise().getId(), student.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Participation " + participation.getId() + " does not have a student exam!"));
            return !ExamService.shouldStudentSeeResult(studentExam, participation);
        }
        return false;
    }
}
