package de.tum.cit.aet.artemis.programming.web.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.errors.CanceledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCServletService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.RepositoryParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Unit tests for the online editor endpoints of a student's participation repository.
 * <p>
 * Everything here is reached with a participation id from the URL, and most of it is reachable by a student, so the
 * access checks are the substance: reading somebody else's build log, or the content of a repository the student is not
 * working in, has to be refused even though the ids involved are valid. The file update additionally has to translate
 * every way the checkout can fail into a distinct status, because the editor reacts differently to each of them.
 */
@ExtendWith(MockitoExtension.class)
class RepositoryProgrammingExerciseParticipationResourceTest {

    private static final long PARTICIPATION_ID = 10L;

    private static final long EXERCISE_ID = 7L;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AuthorizationCheckService authCheckService;

    @Mock
    private ParticipationAuthorizationCheckService participationAuthCheckService;

    @Mock
    private GitService gitService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ProgrammingExerciseParticipationService participationService;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ParticipationTestRepository participationRepository;

    @Mock
    private BuildLogEntryService buildLogService;

    @Mock
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Mock
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Mock
    private RepositoryAccessService repositoryAccessService;

    @Mock
    private LocalVCServletService localVCServletService;

    @Mock
    private RepositoryParticipationService repositoryParticipationService;

    @Mock
    private Repository repository;

    private RepositoryProgrammingExerciseParticipationResource resource;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation participation;

    private User user;

    @BeforeEach
    void setUp() {
        resource = new RepositoryProgrammingExerciseParticipationResource(userRepository, authCheckService, participationAuthCheckService, gitService, repositoryService,
                participationService, programmingExerciseRepository, participationRepository, buildLogService, programmingSubmissionRepository, submissionPolicyRepository,
                repositoryAccessService, Optional.of(localVCServletService), repositoryParticipationService);
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        participation.setBranch("main");
        participation.setProgrammingExercise(exercise);
        participation.setRepositoryUri("https://artemis.example.com/git/ABC/abc-student.git");
        user = new User();
        user.setId(1L);
        user.setLogin("ge12abc");
    }

    /**
     * The participation exists, belongs to a programming exercise, and the access check passes.
     */
    private void withAccessibleParticipation() throws Exception {
        lenient().when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        lenient().when(programmingExerciseRepository.getProgrammingExerciseFromParticipation(participation)).thenReturn(exercise);
        lenient().when(userRepository.getUserWithAuthorities()).thenReturn(user);
        lenient().when(repositoryParticipationService.getRepositoryFromGitService(anyBoolean(), any())).thenReturn(repository);
    }

    @Test
    void getRepository_attachesTheSubmissionPolicyBeforeTheAccessIsChecked() throws Exception {
        // The number of allowed submissions is part of the access decision, so it has to be on the exercise before the check.
        withAccessibleParticipation();
        var policy = new de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy();
        when(submissionPolicyRepository.findByProgrammingExerciseId(EXERCISE_ID)).thenReturn(policy);

        var found = resource.getRepository(PARTICIPATION_ID, RepositoryActionType.READ, true, false);

        assertThat(found).isSameAs(repository);
        assertThat(exercise.getSubmissionPolicy()).isSameAs(policy);
        verify(repositoryAccessService).checkAccessRepositoryElseThrow(participation, user, exercise, RepositoryActionType.READ);
    }

    @Test
    void getRepository_forAParticipationOfAnotherExerciseType_isRefused() {
        // The id comes from the URL, so it can name a text or modeling participation; that must not reach the git service.
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(new StudentParticipation());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resource.getRepository(PARTICIPATION_ID, RepositoryActionType.READ, true, false));
    }

    @Test
    void getRepository_forAParticipationWhoseExerciseIsGone_isRefused() {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipation(participation)).thenReturn(null);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resource.getRepository(PARTICIPATION_ID, RepositoryActionType.READ, true, false));
    }

    @Test
    void getRepositoryUri_isTheUriOfThatParticipation() {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);

        assertThat(resource.getRepositoryUri(PARTICIPATION_ID).toString()).isEqualTo(participation.getVcsRepositoryUri().toString());
    }

    @Test
    void canAccessRepository_asksTheParticipationAuthorizationCheck() {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        when(participationAuthCheckService.canAccessParticipation(participation)).thenReturn(true);

        assertThat(resource.canAccessRepository(PARTICIPATION_ID)).isTrue();
    }

    @Test
    void canAccessRepository_forAParticipationOfAnotherExerciseType_isRefused() {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(new StudentParticipation());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resource.canAccessRepository(PARTICIPATION_ID));
    }

    @Test
    void getBranch_ofAStudentParticipationIsTheBranchThatParticipationWasCreatedOn() {
        // A student participation can sit on a different branch than the exercise, and committing to the wrong one loses the work.
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);

        assertThat(resource.getOrRetrieveBranchOfDomainObject(PARTICIPATION_ID)).isEqualTo("main");
    }

    @Test
    void getBranch_ofASolutionParticipationIsTheBranchOfTheExercise() {
        var solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(PARTICIPATION_ID);
        solutionParticipation.setProgrammingExercise(exercise);
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(solutionParticipation);
        when(programmingExerciseRepository.findBranchByExerciseId(EXERCISE_ID)).thenReturn("develop");

        assertThat(resource.getOrRetrieveBranchOfDomainObject(PARTICIPATION_ID)).isEqualTo("develop");
    }

    @Test
    void getBranch_forAParticipationOfAnotherExerciseType_isRefused() {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(new StudentParticipation());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resource.getOrRetrieveBranchOfDomainObject(PARTICIPATION_ID));
    }

    @Test
    void getFilesForPlagiarismView_readsTheRepositoryThroughThePlagiarismSpecificLookup() throws Exception {
        // The plagiarism view is shown to people who are not allowed into the editor, so it has its own access path.
        when(repositoryParticipationService.getRepositoryForPlagiarismView(PARTICIPATION_ID)).thenReturn(repository);
        when(repositoryService.getFiles(repository)).thenReturn(java.util.Map.of("Main.java", de.tum.cit.aet.artemis.programming.domain.FileType.FILE));

        var response = resource.getFilesForPlagiarismView(PARTICIPATION_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("Main.java");
    }

    @Test
    void getFilesAtCommit_withoutAParticipationId_isABadRequest() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> resource.getFilesAtCommit("abc123", null, null));
    }

    @Test
    void getFilesAtCommit_readsTheParticipationRepositoryForAStudent() throws Exception {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipationElseThrow(participation)).thenReturn(exercise);
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        when(repositoryService.getFilesContentAtCommit(exercise, "abc123", null, participation, null)).thenReturn(java.util.Map.of("Main.java", "content"));

        var response = resource.getFilesAtCommit("abc123", PARTICIPATION_ID, null);

        assertThat(response.getBody()).containsEntry("Main.java", "content");
        // Reading the own participation is a student level operation, so no editor check is made.
        verify(authCheckService, never()).checkHasAtLeastRoleForExerciseElseThrow(any(), any(), any());
    }

    @Test
    void getFilesAtCommit_forAnotherRepositoryOfTheExercise_additionallyRequiresEditorRights() throws Exception {
        // Read access to the own participation says nothing about the solution repository, which holds the answers.
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipationElseThrow(participation)).thenReturn(exercise);
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        doThrow(new AccessForbiddenException("not an editor")).when(authCheckService).checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resource.getFilesAtCommit("abc123", PARTICIPATION_ID, RepositoryType.SOLUTION));

        verify(repositoryService, never()).getFilesContentAtCommit(any(), any(), any(), any(), any());
    }

    // --- the editor endpoints --------------------------------------------------------------------------------------

    private static jakarta.servlet.http.HttpServletRequest requestWithBody(String body) throws Exception {
        var request = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        var bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(request.getInputStream()).thenReturn(new jakarta.servlet.ServletInputStream() {

            private final java.io.ByteArrayInputStream delegate = new java.io.ByteArrayInputStream(bytes);

            @Override
            public boolean isFinished() {
                return delegate.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(jakarta.servlet.ReadListener readListener) {
                // not used by the endpoints under test
            }

            @Override
            public int read() {
                return delegate.read();
            }
        });
        return request;
    }

    @Test
    void getFiles_listsTheFilesOfTheParticipationRepository() throws Exception {
        withAccessibleParticipation();
        when(repositoryService.getFiles(repository)).thenReturn(java.util.Map.of("Main.java", de.tum.cit.aet.artemis.programming.domain.FileType.FILE));

        assertThat(resource.getFiles(PARTICIPATION_ID).getBody()).containsKey("Main.java");
    }

    @Test
    void getFile_readsTheRequestedFile() throws Exception {
        withAccessibleParticipation();
        when(repositoryService.getFileFromRepository("Main.java", repository)).thenReturn(org.springframework.http.ResponseEntity.ok("content".getBytes()));

        assertThat(resource.getFile(PARTICIPATION_ID, "Main.java").getBody()).asString().isEqualTo("content");
    }

    @Test
    void getFileForPlagiarismView_readsTheFileThroughThePlagiarismSpecificLookup() throws Exception {
        when(repositoryParticipationService.getRepositoryForPlagiarismView(PARTICIPATION_ID)).thenReturn(repository);
        when(repositoryService.getFileFromRepository("Main.java", repository)).thenReturn(org.springframework.http.ResponseEntity.ok("content".getBytes()));

        assertThat(resource.getFileForPlagiarismView(PARTICIPATION_ID, "Main.java").getBody()).asString().isEqualTo("content");
    }

    @Test
    void getFilesWithContent_canLeaveTheBinariesOutToKeepThePayloadSmall() throws Exception {
        withAccessibleParticipation();
        when(repositoryService.getFilesContentFromWorkingCopy(repository, true)).thenReturn(java.util.Map.of("Main.java", "content"));

        assertThat(resource.getFilesWithContent(PARTICIPATION_ID, true).getBody()).containsEntry("Main.java", "content");
    }

    @Test
    void createFile_writesTheBodyOfTheRequestIntoTheRepository() throws Exception {
        withAccessibleParticipation();

        assertThat(resource.createFile(PARTICIPATION_ID, "Main.java", requestWithBody("public class Main {}")).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).createFile(eq(repository), eq("Main.java"), any());
    }

    @Test
    void createFolder_createsTheFolderInTheRepository() throws Exception {
        withAccessibleParticipation();

        assertThat(resource.createFolder(PARTICIPATION_ID, "src", requestWithBody("")).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).createFolder(eq(repository), eq("src"), any());
    }

    @Test
    void renameFile_movesTheFileInTheRepository() throws Exception {
        withAccessibleParticipation();
        var fileMove = new de.tum.cit.aet.artemis.programming.dto.FileMove("Old.java", "New.java");

        assertThat(resource.renameFile(PARTICIPATION_ID, fileMove).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).renameFile(repository, fileMove);
    }

    @Test
    void deleteFile_removesTheFileFromTheRepository() throws Exception {
        withAccessibleParticipation();

        assertThat(resource.deleteFile(PARTICIPATION_ID, "Main.java").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).deleteFile(repository, "Main.java");
    }

    @Test
    void pullChanges_pullsAndClosesTheRepository() throws Exception {
        withAccessibleParticipation();

        assertThat(resource.pullChanges(PARTICIPATION_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).pullChanges(repository);
        verify(repository).close();
    }

    @Test
    void commitChanges_commitsAndLetsTheServerProcessThePush() throws Exception {
        withAccessibleParticipation();
        when(userRepository.getUser()).thenReturn(user);
        when(repositoryService.commitChanges(repository, user)).thenReturn("commit-hash");
        when(repositoryService.savePreliminaryCodeEditorAccessLog(repository, user, PARTICIPATION_ID)).thenReturn(Optional.empty());

        assertThat(resource.commitChanges(PARTICIPATION_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(localVCServletService).processNewPush(eq(null), eq(repository), eq(user), any(), any(), eq(Optional.empty()), eq("commit-hash"));
    }

    @Test
    void resetToLastCommit_throwsAwayTheUncommittedChanges() throws Exception {
        withAccessibleParticipation();

        assertThat(resource.resetToLastCommit(PARTICIPATION_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gitService).resetToOriginHead(repository);
    }

    @Test
    void getStatus_reportsWhetherTheWorkingCopyIsClean() throws Exception {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(participation);
        when(participationAuthCheckService.canAccessParticipation(participation)).thenReturn(true);
        when(repositoryService.isWorkingCopyClean(any(), eq("main"))).thenReturn(true);

        assertThat(resource.getStatus(PARTICIPATION_ID).getBody().repositoryStatus()).isEqualTo(de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTOType.CLEAN);
    }

    // --- how a failing checkout reaches the editor -----------------------------------------------------------------

    @Test
    void updateParticipationFiles_forAParticipationThatDoesNotExist_isNotFound() {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenThrow(new EntityNotFoundException("Participation", PARTICIPATION_ID));

        assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> resource.updateParticipationFiles(PARTICIPATION_ID, List.of(), false))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateParticipationFiles_forAParticipationOfAnotherExerciseType_isABadRequest() {
        when(participationRepository.findByIdElseThrow(PARTICIPATION_ID)).thenReturn(new StudentParticipation());

        assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> resource.updateParticipationFiles(PARTICIPATION_ID, List.of(), false))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateParticipationFiles_withoutPermission_isRefused() throws Exception {
        withAccessibleParticipation();
        doThrow(new AccessForbiddenException("not yours")).when(repositoryAccessService).checkAccessRepositoryElseThrow(any(), any(), any(), any());

        assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> resource.updateParticipationFiles(PARTICIPATION_ID, List.of(), false))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateParticipationFiles_whenTheRepositoryCannotBeReached_isReportedAsUnavailable() throws Exception {
        withAccessibleParticipation();
        when(repositoryParticipationService.getRepositoryFromGitService(anyBoolean(), any())).thenThrow(new CanceledException("the git server is gone"));

        assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> resource.updateParticipationFiles(PARTICIPATION_ID, List.of(), false))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void updateParticipationFiles_savesTheFilesWhenEverythingIsInOrder() throws Exception {
        withAccessibleParticipation();

        var response = resource.updateParticipationFiles(PARTICIPATION_ID, List.of(), false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- build logs ------------------------------------------------------------------------------------------------

    private static ProgrammingSubmission submissionWithResult(long submissionId, long resultId, boolean buildFailed) {
        var submission = new ProgrammingSubmission();
        submission.setId(submissionId);
        submission.setBuildFailed(buildFailed);
        var result = new Result();
        result.setId(resultId);
        submission.setResults(Set.of(result));
        return submission;
    }

    @Test
    void getBuildLogs_forAParticipationWithoutASubmission_returnsNothing() {
        participation.setSubmissions(Set.of());
        when(participationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(participation);

        assertThat(resource.getBuildLogs(PARTICIPATION_ID, Optional.empty()).getBody()).isEmpty();
    }

    @Test
    void getBuildLogs_forABuildThatDidNotFail_returnsNothing() {
        // The logs of a successful build are not kept, and returning an empty list is what tells the client to show the result instead.
        participation.setSubmissions(Set.of(submissionWithResult(50L, 90L, false)));
        when(participationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(participation);

        assertThat(resource.getBuildLogs(PARTICIPATION_ID, Optional.empty()).getBody()).isEmpty();
    }

    @Test
    void getBuildLogs_forAFailedBuild_returnsTheLogs() {
        var submission = submissionWithResult(50L, 90L, true);
        participation.setSubmissions(Set.of(submission));
        var logs = List.of(new BuildLogEntry(java.time.ZonedDateTime.now(), "compilation failed"));
        when(participationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(participation);
        when(buildLogService.getLatestBuildLogs(submission)).thenReturn(logs);

        assertThat(resource.getBuildLogs(PARTICIPATION_ID, Optional.empty()).getBody()).isEqualTo(logs);
    }

    @Test
    void getBuildLogs_forAResultOfAnotherParticipation_isRefused() {
        // The result id is a request parameter, so without this check a student could read the build log of any submission.
        participation.setSubmissions(Set.of(submissionWithResult(50L, 90L, true)));
        var otherParticipation = new ProgrammingExerciseStudentParticipation();
        otherParticipation.setId(999L);
        var otherSubmission = submissionWithResult(60L, 91L, true);
        otherSubmission.setParticipation(otherParticipation);
        when(participationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(participation);
        when(programmingSubmissionRepository.findByResultIdElseThrow(91L)).thenReturn(otherSubmission);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resource.getBuildLogs(PARTICIPATION_ID, Optional.of(91L)));
    }

    @Test
    void getBuildLogs_forAnEarlierResultOfTheSameParticipation_returnsThatSubmissionsLogs() {
        // A student can open an older result, whose submission is not the latest one on the participation.
        participation.setSubmissions(Set.of(submissionWithResult(50L, 90L, true)));
        var earlier = submissionWithResult(40L, 80L, true);
        earlier.setParticipation(participation);
        var logs = List.of(new BuildLogEntry(java.time.ZonedDateTime.now(), "an older failure"));
        when(participationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(participation);
        when(programmingSubmissionRepository.findByResultIdElseThrow(80L)).thenReturn(earlier);
        when(buildLogService.getLatestBuildLogs(earlier)).thenReturn(logs);

        assertThat(resource.getBuildLogs(PARTICIPATION_ID, Optional.of(80L)).getBody()).isEqualTo(logs);
    }

    @Test
    void getBuildLogs_forSomebodyWhoMayNotSeeTheParticipation_isRefused() {
        when(participationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(PARTICIPATION_ID)).thenReturn(participation);
        doThrow(new AccessForbiddenException("not yours")).when(participationAuthCheckService).checkCanAccessParticipationElseThrow(participation);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> resource.getBuildLogs(PARTICIPATION_ID, Optional.empty()));
    }

    @Test
    void getFilesWithInformationAboutChange_comparesTheStudentsWorkAgainstTheTemplate() throws Exception {
        // The tutor's diff view is driven by this, and comparing against the wrong repository marks everything as changed.
        withAccessibleParticipation();
        var templateParticipation = new de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation();
        templateParticipation.setId(20L);
        templateParticipation.setProgrammingExercise(exercise);
        exercise.setTemplateParticipation(templateParticipation);
        var templateRepository = org.mockito.Mockito.mock(Repository.class);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(EXERCISE_ID)).thenReturn(exercise);
        when(participationRepository.findByIdElseThrow(20L)).thenReturn(templateParticipation);
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipation(templateParticipation)).thenReturn(exercise);
        when(repositoryParticipationService.getRepositoryFromGitService(anyBoolean(), eq(participation))).thenReturn(repository);
        when(repositoryParticipationService.getRepositoryFromGitService(anyBoolean(), eq(templateParticipation))).thenReturn(templateRepository);
        when(repositoryService.getFilesWithInformationAboutChange(repository, templateRepository)).thenReturn(java.util.Map.of("Main.java", true));

        var response = resource.getFilesWithInformationAboutChange(PARTICIPATION_ID);

        assertThat(response.getBody()).containsEntry("Main.java", true);
    }
}
