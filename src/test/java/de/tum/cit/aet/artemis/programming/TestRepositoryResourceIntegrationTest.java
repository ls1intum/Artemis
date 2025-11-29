package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.File;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.dto.FileMove;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseEditorSyncEventDTO;
import de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTO;
import de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTOType;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepositoryUriUtil;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.web.repository.FileSubmission;

class TestRepositoryResourceIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "testrepositoryresourceint";

    private final String testRepoBaseUrl = "/api/programming/test-repository/";

    private ProgrammingExercise programmingExercise;

    private final String currentLocalFileName = "currentFileName";

    private final String currentLocalFileContent = "testContent";

    private final String currentLocalFolderName = "currentFolderName";

    private final LocalRepository testRepo = new LocalRepository(defaultBranch);

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = courseUtilService.addEmptyCourse();
        programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));

        // Instantiate the remote repository as non-bare so its files can be manipulated
        testRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo", false);

        // add file to the repository folder
        Path filePath = Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the repository folder
        filePath = Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(filePath);

        var testRepoUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(testRepo.workingCopyGitRepoFile, localVCBasePath));
        programmingExercise.setTestRepositoryUri(testRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUri),
                eq(true), anyBoolean());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUri),
                eq(false), anyBoolean());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUri),
                eq(true), anyString(), anyBoolean());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUri),
                eq(false), anyString(), anyBoolean());
    }

    @AfterEach
    void tearDown() throws IOException {
        reset(gitService);
        testRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFiles() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        var files = request.getMap(testRepoBaseUrl + programmingExercise.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetFilesAsStudent_accessForbidden() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        request.getMap(testRepoBaseUrl + programmingExercise.getId() + "/files", HttpStatus.FORBIDDEN, String.class, FileType.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/newFile")).doesNotExist();
        params.add("file", "newFile");
        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.OK, params);
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/newFile")).isRegularFile();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile_alreadyExists() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat((Path.of(testRepo.workingCopyGitRepoFile + "/newFile"))).doesNotExist();
        params.add("file", "newFile");

        doReturn(Optional.of(true)).when(gitService).getFileByName(any(), any());
        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile_invalidRepository() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat((Path.of(testRepo.workingCopyGitRepoFile + "/newFile"))).doesNotExist();
        params.add("file", "newFile");

        Repository mockRepository = mock(Repository.class);
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), eq(true), anyBoolean());
        doReturn(testRepo.workingCopyGitRepoFile.toPath()).when(mockRepository).getLocalPath();
        doReturn(false).when(mockRepository).isValidFile(any());
        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFolder() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/newFolder")).doesNotExist();
        params.add("folder", "newFolder");
        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/newFolder")).isDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFolderBroadcastsSynchronizationUpdate() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("folder", "newFolder");

        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/folder", HttpStatus.OK, params);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/" + programmingExercise.getId() + "/synchronization"), captor.capture());
        assertThat(captor.getValue().target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat((Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName))).exists();
        String newLocalFileName = "newFileName";
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + newLocalFileName)).doesNotExist();
        FileMove fileMove = new FileMove(currentLocalFileName, newLocalFileName);
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).doesNotExist();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + newLocalFileName)).exists();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFileBroadcastsSynchronizationUpdate() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        FileMove fileMove = new FileMove(currentLocalFileName, "newFileName");

        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.OK, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/" + programmingExercise.getId() + "/synchronization"), captor.capture());
        assertThat(captor.getValue().target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFile_alreadyExists() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        FileMove fileMove = createRenameFileMove();

        doReturn(Optional.empty()).when(gitService).getFileByName(any(), any());
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.NOT_FOUND, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFile_invalidExistingFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        FileMove fileMove = createRenameFileMove();

        doReturn(Optional.of(testRepo.workingCopyGitRepoFile)).when(gitService).getFileByName(any(), eq(fileMove.currentFilePath()));

        Repository mockRepository = mock(Repository.class);
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), eq(true), anyBoolean());
        doReturn(testRepo.workingCopyGitRepoFile.toPath()).when(mockRepository).getLocalPath();
        doReturn(false).when(mockRepository).isValidFile(argThat(file -> file.getName().contains(currentLocalFileName)));
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.BAD_REQUEST, null);
    }

    private FileMove createRenameFileMove() {
        String newLocalFileName = "newFileName";

        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + newLocalFileName)).doesNotExist();

        return new FileMove(currentLocalFileName, newLocalFileName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFolder() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFolderName)).exists();
        String newLocalFolderName = "newFolderName";
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + newLocalFolderName)).doesNotExist();
        FileMove fileMove = new FileMove(currentLocalFolderName, newLocalFolderName);
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFolderName)).doesNotExist();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + newLocalFolderName)).exists();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFolderBroadcastsSynchronizationUpdate() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        FileMove fileMove = new FileMove(currentLocalFolderName, "newFolderName");

        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.OK, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/" + programmingExercise.getId() + "/synchronization"), captor.capture());
        assertThat(captor.getValue().target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);
        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.OK, params);
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).doesNotExist();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFileBroadcastsSynchronizationUpdate() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);

        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.OK, params);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/" + programmingExercise.getId() + "/synchronization"), captor.capture());
        assertThat(captor.getValue().target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_notFound() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);

        doReturn(Optional.empty()).when(gitService).getFileByName(any(), any());

        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.NOT_FOUND, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_invalidFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);

        doReturn(Optional.of(testRepo.workingCopyGitRepoFile)).when(gitService).getFileByName(any(), eq(currentLocalFileName));

        Repository mockRepository = mock(Repository.class);
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), eq(true), anyBoolean());
        doReturn(testRepo.workingCopyGitRepoFile.toPath()).when(mockRepository).getLocalPath();
        doReturn(false).when(mockRepository).isValidFile(argThat(file -> file.getName().contains(currentLocalFileName)));

        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_validFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);

        File mockFile = mock(File.class);
        doReturn(Optional.of(mockFile)).when(gitService).getFileByName(any(), eq(currentLocalFileName));
        doReturn(currentLocalFileName).when(mockFile).getName();
        doReturn(false).when(mockFile).isFile();

        Repository mockRepository = mock(Repository.class);
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), eq(true), anyBoolean());
        doReturn(testRepo.workingCopyGitRepoFile.toPath()).when(mockRepository).getLocalPath();
        doReturn(true).when(mockRepository).isValidFile(argThat(file -> file.getName().contains(currentLocalFileName)));

        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.OK, params);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCommitChanges() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/commit", null, HttpStatus.OK, null);
        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");
        var testRepoCommits = testRepo.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getName()).isEqualTo(testRepoCommits.getFirst().getAuthorIdent().getName());
    }

    private List<FileSubmission> getFileSubmissions() {
        List<FileSubmission> fileSubmissions = new ArrayList<>();
        FileSubmission fileSubmission = new FileSubmission();
        fileSubmission.setFileName(currentLocalFileName);
        fileSubmission.setFileContent("updatedFileContent");
        fileSubmissions.add(fileSubmission);
        return fileSubmissions;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFiles() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();
        request.put(testRepoBaseUrl + programmingExercise.getId() + "/files?commit=false", getFileSubmissions(), HttpStatus.OK);

        Path filePath = Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName);
        assertThat(filePath).hasContent("updatedFileContent");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFilesCommitFalseDoesNotBroadcastSynchronizationUpdate() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        reset(websocketMessagingService);

        request.put(testRepoBaseUrl + programmingExercise.getId() + "/files?commit=false", List.of(), HttpStatus.OK);

        verify(websocketMessagingService, never()).sendMessage(eq("/topic/programming-exercises/" + programmingExercise.getId() + "/synchronization"), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetBroadcastsSynchronizationUpdate() throws Exception {
        programmingExerciseRepository.save(programmingExercise);

        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/reset", null, HttpStatus.OK, null);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/" + programmingExercise.getId() + "/synchronization"), captor.capture());
        assertThat(captor.getValue().target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFilesBroadcastsSynchronizationUpdate() throws Exception {
        programmingExerciseRepository.save(programmingExercise);

        request.put(testRepoBaseUrl + programmingExercise.getId() + "/files?commit=false", getFileSubmissions(), HttpStatus.OK);

        var captor = ArgumentCaptor.forClass(ProgrammingExerciseEditorSyncEventDTO.class);
        verify(websocketMessagingService).sendMessage(eq("/topic/programming-exercises/" + programmingExercise.getId() + "/synchronization"), captor.capture());

        var synchronizationMessage = captor.getValue();
        assertThat(synchronizationMessage.target()).isEqualTo(ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY);
        assertThat(synchronizationMessage.auxiliaryRepositoryId()).isNull();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFilesAndCommit() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();

        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        request.put(testRepoBaseUrl + programmingExercise.getId() + "/files?commit=true", getFileSubmissions(), HttpStatus.OK);

        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

        Path filePath = Path.of(testRepo.workingCopyGitRepoFile + "/" + currentLocalFileName);
        assertThat(filePath).hasContent("updatedFileContent");

        var testRepoCommits = testRepo.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getName()).isEqualTo(testRepoCommits.getFirst().getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void testSaveFiles_accessForbidden() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        // student1 should not have access to instructor1's tests repository even if they assume an INSTRUCTOR role.
        request.put(testRepoBaseUrl + programmingExercise.getId() + "/files?commit=true", List.of(), HttpStatus.FORBIDDEN);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPullChanges() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        String fileName = "remoteFile";

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/commit", null, HttpStatus.OK, null);
        try (var remoteRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.remoteBareGitRepoFile.toPath(), null)) {

            // Create file in the remote repository
            Path filePath = Path.of(testRepo.remoteBareGitRepoFile + "/" + fileName);
            Files.createFile(filePath);

            // Check if the file exists in the remote repository and that it doesn't yet exist in the local repository
            assertThat(Path.of(testRepo.remoteBareGitRepoFile + "/" + fileName)).exists();
            assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + fileName)).doesNotExist();

            // Stage all changes and make a second commit in the remote repository
            gitService.stageAllChanges(remoteRepository);
            GitService.commit(testRepo.remoteBareGitRepo).setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();

            // Checks if the current commit is not equal on the local and the remote repository
            assertThat(testRepo.getAllLocalCommits().getFirst()).isNotEqualTo(testRepo.getAllOriginCommits().getFirst());

            // Execute the Rest call
            request.get(testRepoBaseUrl + programmingExercise.getId() + "/pull", HttpStatus.OK, Void.class);

            // Check if the current commit is the same on the local and the remote repository and if the file exists on the local repository
            assertThat(testRepo.getAllLocalCommits().getFirst()).isEqualTo(testRepo.getAllOriginCommits().getFirst());
            assertThat(Path.of(testRepo.workingCopyGitRepoFile + "/" + fileName)).exists();
        }
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetToLastCommit() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        String fileName = "testFile";
        try (var localRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.workingCopyGitRepoFile.toPath(), null);
                var remoteRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.remoteBareGitRepoFile.toPath(), null)) {

            // Check status of git before the commit
            var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

            // Create a commit for the local and the remote repository
            request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/commit", null, HttpStatus.OK, null);

            // Check status of git after the commit
            var receivedStatusAfterCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

            // Create file in the local repository and commit it
            Path localFilePath = Path.of(testRepo.workingCopyGitRepoFile + "/" + fileName);
            var localFile = Files.createFile(localFilePath).toFile();
            // write content to the created file
            FileUtils.write(localFile, "local", Charset.defaultCharset());
            gitService.stageAllChanges(localRepo);
            GitService.commit(testRepo.workingCopyGitRepo).setMessage("local").call();

            // Create file in the remote repository and commit it
            Path remoteFilePath = Path.of(testRepo.remoteBareGitRepoFile + "/" + fileName);
            var remoteFile = Files.createFile(remoteFilePath).toFile();
            // write content to the created file
            FileUtils.write(remoteFile, "remote", Charset.defaultCharset());
            gitService.stageAllChanges(remoteRepo);
            GitService.commit(testRepo.remoteBareGitRepo).setMessage("remote").call();

            // Merge the two and a conflict will occur
            testRepo.workingCopyGitRepo.fetch().setRemote("origin").call();
            List<Ref> refs = testRepo.workingCopyGitRepo.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            var result = testRepo.workingCopyGitRepo.merge().include(refs.getFirst().getObjectId()).setStrategy(MergeStrategy.RESOLVE).call();
            var status = testRepo.workingCopyGitRepo.status().call();
            assertThat(status.getConflicting()).isNotEmpty();
            assertThat(result.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);

            // Execute the reset Rest call
            request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/reset", null, HttpStatus.OK, null);

            // Check the git status after the reset
            status = testRepo.workingCopyGitRepo.status().call();
            assertThat(status.getConflicting()).isEmpty();
            assertThat(testRepo.getAllLocalCommits().getFirst()).isEqualTo(testRepo.getAllOriginCommits().getFirst());
            var receivedStatusAfterReset = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusAfterReset.repositoryStatus()).hasToString("CLEAN");
        }
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStatus() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);

        // The current status is "uncommited changes", since we added files and folders, but we didn't commit yet
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        // Perform a commit to check if the status changes
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/commit", null, HttpStatus.OK, null);

        // Check if the status of git is "clean" after the commit
        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void testGetStatus_cannotAccessRepository() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        // student1 should not have access to instructor1's tests repository even if they assume the role of an INSTRUCTOR.
        request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.FORBIDDEN, RepositoryStatusDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRepositoryState() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        var status = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(status.repositoryStatus()).isEqualTo(RepositoryStatusDTOType.UNCOMMITTED_CHANGES);
        // TODO: also test other states
    }
}
