package de.tum.in.www1.artemis.web.rest.programming;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.web.rest.dto.CommitInfoDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseParticipationResource {

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

    public ProgrammingExerciseParticipationResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ResultRepository resultRepository,
            ParticipationRepository participationRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingSubmissionService submissionService, ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            ResultService resultService, ParticipationAuthorizationCheckService participationAuthCheckService) {
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.participationRepository = participationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.resultRepository = resultRepository;
        this.submissionService = submissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.resultService = resultService;
        this.participationAuthCheckService = participationAuthCheckService;
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

        hasAccessToParticipationElseThrow(participation);

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
        return ResponseEntity.ok(programmingExerciseParticipationService.getCommitInfos(participation));
    }

    /**
     * GET /programming-exercise/{exerciseID}/commit-history/{repositoryType} : Get the commit history of a programming exercise repository. The repository type can be TEMPLATE or
     * SOLUTION or TESTS.
     * Here we check is at least a teaching assistant for the exercise.
     *
     * @param exerciseID     the id of the exercise for which to retrieve the commit history
     * @param repositoryType the type of the repository for which to retrieve the commit history
     * @return the ResponseEntity with status 200 (OK) and with body a list of commitInfo DTOs with the commits information of the repository
     */
    @GetMapping("programming-exercise/{exerciseID}/commit-history/{repositoryType}")
    @EnforceAtLeastTutor
    public ResponseEntity<List<CommitInfoDTO>> getCommitHistoryForTemplateSolutionOrTestRepo(@PathVariable long exerciseID, @PathVariable RepositoryType repositoryType) {
        boolean isTemplateRepository = repositoryType.equals(RepositoryType.TEMPLATE);
        boolean isSolutionRepository = repositoryType.equals(RepositoryType.SOLUTION);
        boolean isTestRepository = repositoryType.equals(RepositoryType.TESTS);
        ProgrammingExerciseParticipation participation;

        if (!isTemplateRepository && !isSolutionRepository && !isTestRepository) {
            throw new BadRequestAlertException("Invalid repository type", ENTITY_NAME, "invalidRepositoryType");
        }
        else if (isTemplateRepository) {
            participation = programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(exerciseID);
        }
        else {
            participation = programmingExerciseParticipationService.findSolutionParticipationByProgrammingExerciseId(exerciseID);
        }
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
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
     * @return a redirect to the endpoint returning the files with content
     */
    @GetMapping("programming-exercise-participations/{participationId}/files-content/{commitId}")
    @EnforceAtLeastInstructor
    public ModelAndView redirectGetParticipationRepositoryFiles(@PathVariable long participationId, @PathVariable String commitId) {
        var participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        ProgrammingExercise exercise = programmingExerciseRepository.getProgrammingExerciseFromParticipationElseThrow(participation);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        return new ModelAndView("forward:/api/repository/" + participation.getId() + "/files-content/" + commitId);
    }

    /**
     * GET /programming-exercise/{exerciseId}/participation/{participationId}/files-content-commit-details/{commitId} : Get the content of the files of a programming exercise
     * This method is specifically for the commit details view, where not only Instructors and Admins should have access to the files content as in
     * redirectGetParticipationRepositoryFiles but also students and tutors that have access to the participation.
     *
     * @param exerciseId      the id of the exercise for which to retrieve the files content
     * @param participationId the id of the participation for which to retrieve the files content
     * @param commitId        the id of the commit for which to retrieve the files content
     * @param repositoryType  the type of the repository for which to retrieve the files content
     * @return a redirect to the endpoint returning the files with content
     */
    @GetMapping("programming-exercise/{exerciseId}/participation/{participationId}/files-content-commit-details/{commitId}")
    @EnforceAtLeastStudent
    public ModelAndView redirectGetParticipationRepositoryFilesForCommitsDetailsView(@PathVariable long exerciseId, @PathVariable long participationId,
            @PathVariable String commitId, @RequestParam RepositoryType repositoryType) {
        if (repositoryType == null) {
            throw new BadRequestAlertException("Invalid repository type", ENTITY_NAME, "invalidRepositoryType");
        }
        ProgrammingExerciseParticipation participation;
        if (repositoryType.equals(RepositoryType.USER)) {
            participation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        }
        else {
            if (repositoryType.equals(RepositoryType.TEMPLATE)) {
                participation = programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(exerciseId);
            }
            else {
                // if the repository is TESTS we also want to get the solution participation check to see if the user
                // has access to the participation, as the TESTS repository doesn't have a participation
                participation = programmingExerciseParticipationService.findSolutionParticipationByProgrammingExerciseId(exerciseId);
            }
        }
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);
        return new ModelAndView("forward:/api/repository/" + participation.getId() + "/files-content/" + commitId).addObject("repositoryType", repositoryType);
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

}
