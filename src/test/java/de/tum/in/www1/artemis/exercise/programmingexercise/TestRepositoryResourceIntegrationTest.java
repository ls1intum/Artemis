package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.File;
import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

class TestRepositoryResourceIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "testrepositoryresourceint";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private final String testRepoBaseUrl = "/api/test-repository/";

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
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");

        // add file to the repository folder
        Path filePath = Path.of(testRepo.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the repository folder
        filePath = Path.of(testRepo.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(filePath);

        var testRepoUri = new GitUtilService.MockFileRepositoryUri(testRepo.localRepoFile);
        programmingExercise.setTestRepositoryUri(testRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(testRepoUri, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(testRepoUri, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUri), eq(true),
                any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUri), eq(false),
                any());

        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(programmingExercise);
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
            assertThat(Path.of(testRepo.localRepoFile + "/" + key)).exists();
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
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).exists();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.localRepoFile + "/newFile")).doesNotExist();
        params.add("file", "newFile");
        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.OK, params);
        assertThat(Path.of(testRepo.localRepoFile + "/newFile")).isRegularFile();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile_alreadyExists() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat((Path.of(testRepo.localRepoFile + "/newFile"))).doesNotExist();
        params.add("file", "newFile");

        doReturn(Optional.of(true)).when(gitService).getFileByName(any(), any());
        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile_invalidRepository() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat((Path.of(testRepo.localRepoFile + "/newFile"))).doesNotExist();
        params.add("file", "newFile");

        Repository mockRepository = mock(Repository.class);
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), eq(true));
        doReturn(testRepo.localRepoFile.toPath()).when(mockRepository).getLocalPath();
        doReturn(false).when(mockRepository).isValidFile(any());
        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFolder() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.localRepoFile + "/newFolder")).doesNotExist();
        params.add("folder", "newFolder");
        request.postWithoutResponseBody(testRepoBaseUrl + programmingExercise.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Path.of(testRepo.localRepoFile + "/newFolder")).isDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat((Path.of(testRepo.localRepoFile + "/" + currentLocalFileName))).exists();
        String newLocalFileName = "newFileName";
        assertThat(Path.of(testRepo.localRepoFile + "/" + newLocalFileName)).doesNotExist();
        FileMove fileMove = new FileMove(currentLocalFileName, newLocalFileName);
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).doesNotExist();
        assertThat(Path.of(testRepo.localRepoFile + "/" + newLocalFileName)).exists();
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

        doReturn(Optional.of(testRepo.localRepoFile)).when(gitService).getFileByName(any(), eq(fileMove.currentFilePath()));

        Repository mockRepository = mock(Repository.class);
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), eq(true));
        doReturn(testRepo.localRepoFile.toPath()).when(mockRepository).getLocalPath();
        doReturn(false).when(mockRepository).isValidFile(argThat(file -> file.getName().contains(currentLocalFileName)));
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.BAD_REQUEST, null);
    }

    private FileMove createRenameFileMove() {
        String newLocalFileName = "newFileName";

        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).exists();
        assertThat(Path.of(testRepo.localRepoFile + "/" + newLocalFileName)).doesNotExist();

        return new FileMove(currentLocalFileName, newLocalFileName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFolder() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFolderName)).exists();
        String newLocalFolderName = "newFolderName";
        assertThat(Path.of(testRepo.localRepoFile + "/" + newLocalFolderName)).doesNotExist();
        FileMove fileMove = new FileMove(currentLocalFolderName, newLocalFolderName);
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFolderName)).doesNotExist();
        assertThat(Path.of(testRepo.localRepoFile + "/" + newLocalFolderName)).exists();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);
        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.OK, params);
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).doesNotExist();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_notFound() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);

        doReturn(Optional.empty()).when(gitService).getFileByName(any(), any());

        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.NOT_FOUND, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_invalidFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);

        doReturn(Optional.of(testRepo.localRepoFile)).when(gitService).getFileByName(any(), eq(currentLocalFileName));

        Repository mockRepository = mock(Repository.class);
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), eq(true));
        doReturn(testRepo.localRepoFile.toPath()).when(mockRepository).getLocalPath();
        doReturn(false).when(mockRepository).isValidFile(argThat(file -> file.getName().contains(currentLocalFileName)));

        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_validFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);

        File mockFile = mock(File.class);
        doReturn(Optional.of(mockFile)).when(gitService).getFileByName(any(), eq(currentLocalFileName));
        doReturn(currentLocalFileName).when(mockFile).getName();
        doReturn(false).when(mockFile).isFile();

        Repository mockRepository = mock(Repository.class);
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), eq(true));
        doReturn(testRepo.localRepoFile.toPath()).when(mockRepository).getLocalPath();
        doReturn(true).when(mockRepository).isValidFile(argThat(file -> file.getName().contains(currentLocalFileName)));

        request.delete(testRepoBaseUrl + programmingExercise.getId() + "/file", HttpStatus.OK, params);
    }

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
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
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
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).exists();
        request.put(testRepoBaseUrl + programmingExercise.getId() + "/files?commit=false", getFileSubmissions(), HttpStatus.OK);

        Path filePath = Path.of(testRepo.localRepoFile + "/" + currentLocalFileName);
        assertThat(filePath).hasContent("updatedFileContent");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFilesAndCommit() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat(Path.of(testRepo.localRepoFile + "/" + currentLocalFileName)).exists();

        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        request.put(testRepoBaseUrl + programmingExercise.getId() + "/files?commit=true", getFileSubmissions(), HttpStatus.OK);

        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

        Path filePath = Path.of(testRepo.localRepoFile + "/" + currentLocalFileName);
        assertThat(filePath).hasContent("updatedFileContent");

        var testRepoCommits = testRepo.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void testSaveFiles_accessForbidden() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        // student1 should not have access to instructor1's tests repository even if they assume an INSTRUCTOR role.
        request.put(testRepoBaseUrl + programmingExercise.getId() + "/files?commit=true", List.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPullChanges() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        String fileName = "remoteFile";

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/commit", null, HttpStatus.OK, null);
        try (var remoteRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.originRepoFile.toPath(), null)) {

            // Create file in the remote repository
            Path filePath = Path.of(testRepo.originRepoFile + "/" + fileName);
            Files.createFile(filePath);

            // Check if the file exists in the remote repository and that it doesn't yet exist in the local repository
            assertThat(Path.of(testRepo.originRepoFile + "/" + fileName)).exists();
            assertThat(Path.of(testRepo.localRepoFile + "/" + fileName)).doesNotExist();

            // Stage all changes and make a second commit in the remote repository
            gitService.stageAllChanges(remoteRepository);
            GitService.commit(testRepo.originGit).setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();

            // Checks if the current commit is not equal on the local and the remote repository
            assertThat(testRepo.getAllLocalCommits().get(0)).isNotEqualTo(testRepo.getAllOriginCommits().get(0));

            // Execute the Rest call
            request.get(testRepoBaseUrl + programmingExercise.getId() + "/pull", HttpStatus.OK, Void.class);

            // Check if the current commit is the same on the local and the remote repository and if the file exists on the local repository
            assertThat(testRepo.getAllLocalCommits().get(0)).isEqualTo(testRepo.getAllOriginCommits().get(0));
            assertThat(Path.of(testRepo.localRepoFile + "/" + fileName)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetToLastCommit() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        String fileName = "testFile";
        try (var localRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null);
                var remoteRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.originRepoFile.toPath(), null)) {

            // Check status of git before the commit
            var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

            // Create a commit for the local and the remote repository
            request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/commit", null, HttpStatus.OK, null);

            // Check status of git after the commit
            var receivedStatusAfterCommit = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

            // Create file in the local repository and commit it
            Path localFilePath = Path.of(testRepo.localRepoFile + "/" + fileName);
            var localFile = Files.createFile(localFilePath).toFile();
            // write content to the created file
            FileUtils.write(localFile, "local", Charset.defaultCharset());
            gitService.stageAllChanges(localRepo);
            GitService.commit(testRepo.localGit).setMessage("local").call();

            // Create file in the remote repository and commit it
            Path remoteFilePath = Path.of(testRepo.originRepoFile + "/" + fileName);
            var remoteFile = Files.createFile(remoteFilePath).toFile();
            // write content to the created file
            FileUtils.write(remoteFile, "remote", Charset.defaultCharset());
            gitService.stageAllChanges(remoteRepo);
            GitService.commit(testRepo.originGit).setMessage("remote").call();

            // Merge the two and a conflict will occur
            testRepo.localGit.fetch().setRemote("origin").call();
            List<Ref> refs = testRepo.localGit.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            var result = testRepo.localGit.merge().include(refs.get(0).getObjectId()).setStrategy(MergeStrategy.RESOLVE).call();
            var status = testRepo.localGit.status().call();
            assertThat(status.getConflicting()).isNotEmpty();
            assertThat(result.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);

            // Execute the reset Rest call
            request.postWithoutLocation(testRepoBaseUrl + programmingExercise.getId() + "/reset", null, HttpStatus.OK, null);

            // Check the git status after the reset
            status = testRepo.localGit.status().call();
            assertThat(status.getConflicting()).isEmpty();
            assertThat(testRepo.getAllLocalCommits().get(0)).isEqualTo(testRepo.getAllOriginCommits().get(0));
            var receivedStatusAfterReset = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusAfterReset.repositoryStatus()).hasToString("CLEAN");
        }
    }

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
    void testIsClean() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        doReturn(true).when(gitService).isRepositoryCached(any());
        var status = request.get(testRepoBaseUrl + programmingExercise.getId(), HttpStatus.OK, Map.class);
        assertThat(status).isNotEmpty();
    }
}
