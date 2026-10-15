package de.tum.cit.aet.artemis.programming.web.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.LocalVCServletService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for the online editor endpoints of an auxiliary repository.
 * <p>
 * An auxiliary repository often holds the material an exercise is built from, so the access check has to run before the
 * repository is checked out rather than after. The other half of this class is the mapping from what git reports to what
 * the editor is told: a conflicting checkout, a repository that cannot be reached and a missing permission each have to
 * arrive as a different status, because the editor offers a different way out for each of them and a wrong status sends
 * the instructor down the wrong one.
 */
@ExtendWith(MockitoExtension.class)
class AuxiliaryRepositoryResourceTest {

    private static final long AUXILIARY_REPOSITORY_ID = 12L;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AuthorizationCheckService authCheckService;

    @Mock
    private GitService gitService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private RepositoryAccessService repositoryAccessService;

    @Mock
    private LocalVCServletService localVCServletService;

    @Mock
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Mock
    private Repository repository;

    @Mock
    private Principal principal;

    private AuxiliaryRepositoryResource auxiliaryRepositoryResource;

    private AuxiliaryRepository auxiliaryRepository;

    private User user;

    @BeforeEach
    void setUp() {
        auxiliaryRepositoryResource = new AuxiliaryRepositoryResource(userRepository, authCheckService, gitService, repositoryService, programmingExerciseRepository,
                repositoryAccessService, Optional.of(localVCServletService), auxiliaryRepositoryRepository);
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(7L);
        auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setId(AUXILIARY_REPOSITORY_ID);
        auxiliaryRepository.setName("solution-helpers");
        auxiliaryRepository.setRepositoryUri("https://artemis.example.com/git/ABC/abc-auxiliary.git");
        auxiliaryRepository.setExercise(exercise);
        user = new User();
        user.setId(1L);
        user.setLogin("ge12abc");
        lenient().when(auxiliaryRepositoryRepository.findByIdElseThrow(AUXILIARY_REPOSITORY_ID)).thenReturn(auxiliaryRepository);
    }

    @Test
    void getRepository_checksTheAccessBeforeCheckingTheRepositoryOut() throws Exception {
        // Checking out first would put the repository on disk for a user who is not allowed to see it.
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        doThrow(new AccessForbiddenException("not yours")).when(repositoryAccessService).checkAccessTestOrAuxRepositoryElseThrow(eq(false), any(), eq(user), anyString());

        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> auxiliaryRepositoryResource.getRepository(AUXILIARY_REPOSITORY_ID, RepositoryActionType.READ, true, false));

        verify(gitService, never()).getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean());
    }

    @Test
    void getRepository_checksOutTheRepositoryTheAuxiliaryRepositoryPointsAt() throws Exception {
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        when(gitService.getOrCheckoutRepository(auxiliaryRepository.getVcsRepositoryUri(), true, true)).thenReturn(repository);

        assertThat(auxiliaryRepositoryResource.getRepository(AUXILIARY_REPOSITORY_ID, RepositoryActionType.WRITE, true, true)).isSameAs(repository);
    }

    @Test
    void canAccessRepository_isFalseWhenTheAccessCheckRefuses() {
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        doThrow(new AccessForbiddenException("not yours")).when(repositoryAccessService).checkAccessTestOrAuxRepositoryElseThrow(eq(false), any(), eq(user), anyString());

        assertThat(auxiliaryRepositoryResource.canAccessRepository(AUXILIARY_REPOSITORY_ID)).isFalse();
    }

    @Test
    void canAccessRepository_isTrueWhenTheAccessCheckPasses() {
        when(userRepository.getUserWithAuthorities()).thenReturn(user);

        assertThat(auxiliaryRepositoryResource.canAccessRepository(AUXILIARY_REPOSITORY_ID)).isTrue();
    }

    @Test
    void getRepositoryUri_isTheUriOfTheAuxiliaryRepository() {
        assertThat(auxiliaryRepositoryResource.getRepositoryUri(AUXILIARY_REPOSITORY_ID).toString()).isEqualTo(auxiliaryRepository.getVcsRepositoryUri().toString());
    }

    @Test
    void getOrRetrieveBranchOfDomainObject_readsTheBranchOfThatRepositoryOnly() {
        // The editor commits onto this branch, so reading the branch of a different repository would commit to the wrong place.
        when(auxiliaryRepositoryRepository.findBranchByRepoId(AUXILIARY_REPOSITORY_ID)).thenReturn("main");

        assertThat(auxiliaryRepositoryResource.getOrRetrieveBranchOfDomainObject(AUXILIARY_REPOSITORY_ID)).isEqualTo("main");
    }

    // --- the editor endpoints --------------------------------------------------------------------------------------

    /**
     * The access check passes and the repository is checked out, which is the state every editor endpoint starts from.
     */
    private void withAccessibleRepository() throws Exception {
        lenient().when(userRepository.getUserWithAuthorities()).thenReturn(user);
        lenient().when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean())).thenReturn(repository);
    }

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
    void getFiles_listsTheFilesOfTheAuxiliaryRepository() throws Exception {
        withAccessibleRepository();
        when(repositoryService.getFiles(repository)).thenReturn(java.util.Map.of("Main.java", de.tum.cit.aet.artemis.programming.domain.FileType.FILE));

        var response = auxiliaryRepositoryResource.getFiles(AUXILIARY_REPOSITORY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("Main.java", de.tum.cit.aet.artemis.programming.domain.FileType.FILE);
    }

    @Test
    void getFile_readsTheRequestedFileOfTheAuxiliaryRepository() throws Exception {
        withAccessibleRepository();
        when(repositoryService.getFileFromRepository("Main.java", repository)).thenReturn(org.springframework.http.ResponseEntity.ok("content".getBytes()));

        var response = auxiliaryRepositoryResource.getFile(AUXILIARY_REPOSITORY_ID, "Main.java");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).asString().isEqualTo("content");
    }

    @Test
    void createFile_writesTheBodyOfTheRequestIntoTheRepository() throws Exception {
        withAccessibleRepository();

        var response = auxiliaryRepositoryResource.createFile(AUXILIARY_REPOSITORY_ID, "Main.java", requestWithBody("public class Main {}"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).createFile(eq(repository), eq("Main.java"), any());
    }

    @Test
    void createFolder_createsTheFolderInTheRepository() throws Exception {
        withAccessibleRepository();

        var response = auxiliaryRepositoryResource.createFolder(AUXILIARY_REPOSITORY_ID, "src", requestWithBody(""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).createFolder(eq(repository), eq("src"), any());
    }

    @Test
    void renameFile_movesTheFileInTheRepository() throws Exception {
        withAccessibleRepository();
        var fileMove = new de.tum.cit.aet.artemis.programming.dto.FileMove("Old.java", "New.java");

        var response = auxiliaryRepositoryResource.renameFile(AUXILIARY_REPOSITORY_ID, fileMove);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).renameFile(repository, fileMove);
    }

    @Test
    void deleteFile_removesTheFileFromTheRepository() throws Exception {
        withAccessibleRepository();

        var response = auxiliaryRepositoryResource.deleteFile(AUXILIARY_REPOSITORY_ID, "Main.java");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).deleteFile(repository, "Main.java");
    }

    @Test
    void pullChanges_pullsAndClosesTheRepository() throws Exception {
        withAccessibleRepository();

        var response = auxiliaryRepositoryResource.pullChanges(AUXILIARY_REPOSITORY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repositoryService).pullChanges(repository);
        // The endpoint takes the repository in a try-with-resources, so the working copy must not be left open.
        verify(repository).close();
    }

    @Test
    void commitChanges_commitsAndLetsTheServerProcessThePush() throws Exception {
        // Without the push being processed no build is triggered, so the instructor would commit and never get a result.
        withAccessibleRepository();
        when(userRepository.getUser()).thenReturn(user);
        when(repositoryService.commitChanges(repository, user)).thenReturn("commit-hash");
        when(repositoryService.savePreliminaryCodeEditorAccessLog(repository, user, AUXILIARY_REPOSITORY_ID)).thenReturn(Optional.empty());

        var response = auxiliaryRepositoryResource.commitChanges(AUXILIARY_REPOSITORY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(localVCServletService).processNewPush(eq(null), eq(repository), eq(user), any(), any(), eq(Optional.empty()), eq("commit-hash"));
    }

    @Test
    void resetToLastCommit_throwsAwayTheUncommittedChanges() throws Exception {
        withAccessibleRepository();

        var response = auxiliaryRepositoryResource.resetToLastCommit(AUXILIARY_REPOSITORY_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gitService).resetToOriginHead(repository);
    }

    @Test
    void getStatus_reportsACleanRepositoryAsClean() throws Exception {
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        when(auxiliaryRepositoryRepository.findBranchByRepoId(AUXILIARY_REPOSITORY_ID)).thenReturn("main");
        when(repositoryService.isWorkingCopyClean(any(LocalVCRepositoryUri.class), eq("main"))).thenReturn(true);

        var response = auxiliaryRepositoryResource.getStatus(AUXILIARY_REPOSITORY_ID);

        assertThat(response.getBody().repositoryStatus()).isEqualTo(de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTOType.CLEAN);
    }

    @Test
    void getStatus_reportsARepositoryWithUncommittedChanges() throws Exception {
        // The editor shows the commit button as enabled off this status, so reporting clean would hide the instructor's work.
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        when(auxiliaryRepositoryRepository.findBranchByRepoId(AUXILIARY_REPOSITORY_ID)).thenReturn("main");
        when(repositoryService.isWorkingCopyClean(any(LocalVCRepositoryUri.class), eq("main"))).thenReturn(false);

        var response = auxiliaryRepositoryResource.getStatus(AUXILIARY_REPOSITORY_ID);

        assertThat(response.getBody().repositoryStatus()).isEqualTo(de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTOType.UNCOMMITTED_CHANGES);
    }

    @Test
    void getStatus_reportsAConflictWhenTheWorkingCopyCannotBeBrought() throws Exception {
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        when(auxiliaryRepositoryRepository.findBranchByRepoId(AUXILIARY_REPOSITORY_ID)).thenReturn("main");
        when(repositoryService.isWorkingCopyClean(any(LocalVCRepositoryUri.class), eq("main")))
                .thenThrow(new CheckoutConflictException(List.of("Main.java"), new org.eclipse.jgit.errors.CheckoutConflictException(new String[] { "Main.java" })));

        var response = auxiliaryRepositoryResource.getStatus(AUXILIARY_REPOSITORY_ID);

        assertThat(response.getBody().repositoryStatus()).isEqualTo(de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTOType.CONFLICT);
    }

    @Test
    void getStatus_forSomebodyWhoMayNotSeeTheRepository_isRefused() {
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        doThrow(new AccessForbiddenException("not yours")).when(repositoryAccessService).checkAccessTestOrAuxRepositoryElseThrow(eq(false), any(), eq(user), anyString());

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> auxiliaryRepositoryResource.getStatus(AUXILIARY_REPOSITORY_ID));
    }

    // --- how failures inside an editor operation reach the client --------------------------------------------------

    @Test
    void anOperationOnAPathThatLeavesTheRepositoryIsReportedAsABadRequest() throws Exception {
        // The editor sends the path, so this is the client's mistake and it has to be told so rather than shown a server error.
        withAccessibleRepository();
        doThrow(new IllegalArgumentException("path traversal")).when(repositoryService).deleteFile(repository, "../outside.txt");

        assertThatExceptionOfType(de.tum.cit.aet.artemis.core.exception.BadRequestAlertException.class)
                .isThrownBy(() -> auxiliaryRepositoryResource.deleteFile(AUXILIARY_REPOSITORY_ID, "../outside.txt"));
    }

    @Test
    void creatingAFileThatAlreadyExistsIsReportedAsABadRequest() throws Exception {
        withAccessibleRepository();
        doThrow(new java.nio.file.FileAlreadyExistsException("Main.java")).when(repositoryService).createFile(eq(repository), eq("Main.java"), any());

        assertThatExceptionOfType(de.tum.cit.aet.artemis.core.exception.BadRequestAlertException.class)
                .isThrownBy(() -> auxiliaryRepositoryResource.createFile(AUXILIARY_REPOSITORY_ID, "Main.java", requestWithBody("x")));
    }

    @Test
    void anOperationOnAFileThatIsNotThereIsReportedAsNotFound() throws Exception {
        withAccessibleRepository();
        when(repositoryService.getFileFromRepository("Missing.java", repository)).thenThrow(new java.io.FileNotFoundException("Missing.java"));

        assertThatExceptionOfType(de.tum.cit.aet.artemis.core.exception.EntityNotFoundException.class)
                .isThrownBy(() -> auxiliaryRepositoryResource.getFile(AUXILIARY_REPOSITORY_ID, "Missing.java"));
    }

    @Test
    void anOperationOnARepositoryThatConflictsIsReportedAsAConflict() throws Exception {
        withAccessibleRepository();
        when(userRepository.getUser()).thenReturn(user);
        when(repositoryService.commitChanges(repository, user)).thenThrow(new WrongRepositoryStateException("mid rebase"));

        assertThat(auxiliaryRepositoryResource.commitChanges(AUXILIARY_REPOSITORY_ID).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anOperationThatFailsInGitIsReportedAsAServerError() throws Exception {
        // Nothing the instructor did caused this, so it must not arrive as a client error they would try to correct.
        withAccessibleRepository();
        when(repositoryService.getFileFromRepository("Main.java", repository)).thenThrow(new java.io.IOException("the git server is gone"));

        assertThat(auxiliaryRepositoryResource.getFile(AUXILIARY_REPOSITORY_ID, "Main.java").getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void updateAuxiliaryFiles_savesEveryFileAndReportsTheOnesThatFailedIndividually() throws Exception {
        // One unwritable file must not lose the instructor the edits to all the others, so each file gets its own outcome.
        withPrincipalUser();
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean())).thenReturn(repository);
        var saved = new FileSubmission();
        saved.setFileName("Saved.java");
        saved.setFileContent("content");
        var missing = new FileSubmission();
        missing.setFileName("Missing.java");
        missing.setFileContent("content");
        java.io.File onDisk = java.io.File.createTempFile("aux", ".java");
        onDisk.deleteOnExit();
        when(gitService.getFileByName(repository, "Saved.java")).thenReturn(Optional.of(new de.tum.cit.aet.artemis.programming.domain.File(onDisk, repository)));
        when(gitService.getFileByName(repository, "Missing.java")).thenReturn(Optional.empty());

        var response = auxiliaryRepositoryResource.updateAuxiliaryFiles(AUXILIARY_REPOSITORY_ID, List.of(saved, missing), false, principal);

        assertThat(response.getBody()).containsEntry("Saved.java", null).containsEntry("Missing.java", "File could not be found.");
        assertThat(onDisk).content(java.nio.charset.StandardCharsets.UTF_8).isEqualTo("content");
    }

    private void withPrincipalUser() {
        when(principal.getName()).thenReturn("ge12abc");
        when(userRepository.getUserWithAuthorities("ge12abc")).thenReturn(user);
    }

    @Test
    void updateAuxiliaryFiles_withoutPermission_isRefusedWithoutTouchingTheRepository() {
        withPrincipalUser();
        doThrow(new AccessForbiddenException("not yours")).when(repositoryAccessService).checkAccessTestOrAuxRepositoryElseThrow(eq(true), any(), eq(user), anyString());

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> auxiliaryRepositoryResource.updateAuxiliaryFiles(AUXILIARY_REPOSITORY_ID, List.of(), true, principal))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateAuxiliaryFiles_whenTheCheckoutConflicts_isReportedAsAConflict() throws Exception {
        // The editor offers to discard the local changes on a conflict, which it only does for this status.
        withPrincipalUser();
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean()))
                .thenThrow(new CheckoutConflictException(List.of("Main.java"), new org.eclipse.jgit.errors.CheckoutConflictException(new String[] { "Main.java" })));

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> auxiliaryRepositoryResource.updateAuxiliaryFiles(AUXILIARY_REPOSITORY_ID, List.of(), true, principal))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateAuxiliaryFiles_whenTheRepositoryIsInAWrongState_isReportedAsAConflict() throws Exception {
        withPrincipalUser();
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean())).thenThrow(new WrongRepositoryStateException("mid rebase"));

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> auxiliaryRepositoryResource.updateAuxiliaryFiles(AUXILIARY_REPOSITORY_ID, List.of(), true, principal))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateAuxiliaryFiles_whenTheRepositoryCannotBeReached_isReportedAsUnavailable() throws Exception {
        // Unlike a conflict this is not the instructor's doing, so the editor tells them to try again rather than to resolve anything.
        withPrincipalUser();
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean())).thenThrow(new CanceledException("the server is gone"));

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> auxiliaryRepositoryResource.updateAuxiliaryFiles(AUXILIARY_REPOSITORY_ID, List.of(), true, principal))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void updateAuxiliaryFiles_withoutCommitting_savesTheFilesAndLeavesThemUncommitted() throws Exception {
        // The editor saves on every keystroke and commits separately; committing here would produce a commit per keystroke.
        withPrincipalUser();
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean())).thenReturn(repository);

        var response = auxiliaryRepositoryResource.updateAuxiliaryFiles(AUXILIARY_REPOSITORY_ID, List.of(), false, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(gitService, never()).stageAllChanges(any());
    }
}
