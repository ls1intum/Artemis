package de.tum.cit.aet.artemis.exercise.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.dto.FileMove;
import de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTO;
import de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTOType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseRepositoryService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.web.repository.FileSubmission;

class AuxiliaryRepositoryResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "auxiliaryrepositoryresourceint";

    private final String testRepoBaseUrl = "/api/programming/auxiliary-repositories/";

    @Autowired
    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private ProgrammingExercise programmingExercise;

    private AuxiliaryRepository auxiliaryRepository;

    private final String currentLocalFileName = "currentFileName";

    private final String currentLocalFileContent = "testContent";

    private final String currentLocalFolderName = "currentFolderName";

    private LocalVCTestRepository localAuxiliaryRepo;

    private LocalVCRepositoryUri auxRepoUri;

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));

        // Create a LocalVC auxiliary repository under the expected LocalVC structure
        var projectKey = programmingExercise.getProjectKey();
        String auxSlug = projectKey.toLowerCase(Locale.ROOT) + "-auxiliary";
        localAuxiliaryRepo = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, auxSlug);

        // add file to the repository folder
        Path filePath = Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the repository folder and ensure it is tracked by adding a placeholder file
        filePath = Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + currentLocalFolderName);
        Files.createDirectory(filePath);
        var keepFile = Files.createFile(filePath.resolve(".keep")).toFile();
        FileUtils.write(keepFile, "keep", Charset.defaultCharset());

        // commit and push changes so the remote bare repo has the content
        localAuxiliaryRepo.workingCopy().add().addFilepattern(".").call();
        GitService.commit(localAuxiliaryRepo.workingCopy()).setMessage("seed aux content").call();
        localAuxiliaryRepo.workingCopy().push().setRemote("origin").call();

        // add the auxiliary repository
        auxiliaryRepositoryRepository.deleteAll();
        auxRepoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, auxSlug));
        // programmingExercise.setTestRepositoryUri(auxRepoUri.toString());
        var newAuxiliaryRepo = new AuxiliaryRepository();
        newAuxiliaryRepo.setName("AuxiliaryRepo");
        newAuxiliaryRepo.setRepositoryUri(auxRepoUri.toString());
        newAuxiliaryRepo.setCheckoutDirectory("assignment/src");
        newAuxiliaryRepo.setExercise(programmingExercise);
        programmingExercise.setAuxiliaryRepositories(List.of(newAuxiliaryRepo));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        auxiliaryRepository = programmingExercise.getAuxiliaryRepositories().getFirst();

        // No GitService stubs for happy path; LocalVC will checkout the repository for auxRepoUri
    }

    @AfterEach
    void tearDown() throws IOException {
        if (localAuxiliaryRepo != null) {
            localAuxiliaryRepo.deleteWorkingCopy();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFiles() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        var files = request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetFilesAsStudent_accessForbidden() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.FORBIDDEN, String.class, FileType.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFilesAsInstructor_checkoutConflict() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        Repository conflictedRepository = createMergeConflictInServerClone();
        try {
            request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.CONFLICT, String.class, FileType.class);
        }
        finally {
            deleteLocalClone(conflictedRepository);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + currentLocalFileName)).exists();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "newFile");
        request.postWithoutResponseBody(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.OK, params);
        var files = request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).containsKey("newFile");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile_alreadyExists() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        createFileAndPush("newFile", "existing content");
        params.add("file", "newFile");
        request.postWithoutResponseBody(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFile_invalidRepository() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "../malicious");
        request.postWithoutResponseBody(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateFolder() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("folder", "newFolder");
        request.postWithoutResponseBody(testRepoBaseUrl + auxiliaryRepository.getId() + "/folder", HttpStatus.OK, params);
        var files = request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).containsEntry("newFolder", FileType.FOLDER);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        String newLocalFileName = "newFileName";
        FileMove fileMove = new FileMove(currentLocalFileName, newLocalFileName);
        request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        var files = request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).doesNotContainKey(currentLocalFileName);
        assertThat(files).containsKey(newLocalFileName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFile_alreadyExists() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        deleteFileAndPush(currentLocalFileName);
        FileMove fileMove = new FileMove(currentLocalFileName, "newFileName");
        request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/rename-file", fileMove, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFile_invalidExistingFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        FileMove fileMove = new FileMove("../" + currentLocalFileName, "newFileName");
        request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/rename-file", fileMove, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRenameFolder() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + currentLocalFolderName)).exists();
        String newLocalFolderName = "newFolderName";
        assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + newLocalFolderName)).doesNotExist();
        FileMove fileMove = new FileMove(currentLocalFolderName, newLocalFolderName);
        request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        var files = request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).doesNotContainKey(currentLocalFolderName);
        assertThat(files).containsEntry(newLocalFolderName, FileType.FOLDER);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + currentLocalFileName)).exists();
        params.add("file", currentLocalFileName);
        request.delete(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.OK, params);
        var files = request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).doesNotContainKey(currentLocalFileName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_notFound() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        deleteFileAndPush(currentLocalFileName);

        request.delete(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_invalidFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "../" + currentLocalFileName);
        request.delete(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFile_validFile() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFolderName);
        request.delete(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.OK, params);
        var files = request.getMap(testRepoBaseUrl + auxiliaryRepository.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).doesNotContainKey(currentLocalFolderName);
    }

    // TODO fix tests - breaks in getLocalVCRepositoryUri
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCommitChanges() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/commit", null, HttpStatus.OK, null);
        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");
        var testRepoCommits = localAuxiliaryRepo.workingCopyCommits();
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

    private void createFileAndPush(String relativePath, String content) throws Exception {
        Path filePath = localAuxiliaryRepo.workingCopyPath().resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        FileUtils.write(filePath.toFile(), content, Charset.defaultCharset());
        localAuxiliaryRepo.workingCopy().add().addFilepattern(relativePath).call();
        GitService.commit(localAuxiliaryRepo.workingCopy()).setMessage("create " + relativePath).call();
        localAuxiliaryRepo.workingCopy().push().setRemote("origin").call();
    }

    private void deleteFileAndPush(String relativePath) throws Exception {
        Path filePath = localAuxiliaryRepo.workingCopyPath().resolve(relativePath);
        if (!Files.exists(filePath)) {
            return;
        }
        Files.delete(filePath);
        localAuxiliaryRepo.workingCopy().add().setUpdate(true).addFilepattern(relativePath).call();
        GitService.commit(localAuxiliaryRepo.workingCopy()).setMessage("delete " + relativePath).call();
        localAuxiliaryRepo.workingCopy().push().setRemote("origin").call();
    }

    private Repository createMergeConflictInServerClone() throws Exception {
        Repository repository = gitService.getOrCheckoutRepository(auxRepoUri, true, true);
        try (Git serverGit = Git.wrap(repository)) {
            Path workTree = repository.getWorkTree().toPath();
            Path localFilePath = workTree.resolve(currentLocalFileName);
            FileUtils.write(localFilePath.toFile(), "local change " + UUID.randomUUID(), Charset.defaultCharset());
            serverGit.add().addFilepattern(currentLocalFileName).call();
            GitService.commit(serverGit).setMessage("local conflicting commit").call();

            Path remoteFilePath = localAuxiliaryRepo.workingCopyPath().resolve(currentLocalFileName);
            FileUtils.write(remoteFilePath.toFile(), "remote change " + UUID.randomUUID(), Charset.defaultCharset());
            localAuxiliaryRepo.workingCopy().add().addFilepattern(currentLocalFileName).call();
            GitService.commit(localAuxiliaryRepo.workingCopy()).setMessage("remote conflicting commit").call();
            localAuxiliaryRepo.workingCopy().push().setRemote("origin").call();

            serverGit.fetch().setRemote("origin").call();
            List<Ref> refs = serverGit.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            MergeResult mergeResult = serverGit.merge().include(refs.getFirst().getObjectId()).setStrategy(MergeStrategy.RESOLVE).call();
            assertThat(mergeResult.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);
            assertThat(serverGit.status().call().getConflicting()).isNotEmpty();
        }
        return repository;
    }

    private void deleteLocalClone(Repository repository) throws IOException {
        if (repository != null) {
            gitService.deleteLocalRepository(repository);
        }
    }

    private void deleteRemoteAuxiliaryRepository() throws IOException {
        if (localAuxiliaryRepo.bareRepository() != null) {
            localAuxiliaryRepo.bareRepository().close();
        }
        if (localAuxiliaryRepo.bareRepositoryPath().toFile().exists()) {
            RepositoryExportTestUtil.safeDeleteDirectory(localAuxiliaryRepo.bareRepositoryPath());
        }
        if (localAuxiliaryRepo.workingCopy() != null) {
            localAuxiliaryRepo.workingCopy().close();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFiles() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + currentLocalFileName)).exists();
        request.put(testRepoBaseUrl + auxiliaryRepository.getId() + "/files?commit=false", getFileSubmissions(), HttpStatus.OK);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var updated = request.get(testRepoBaseUrl + auxiliaryRepository.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(new String(updated)).isEqualTo("updatedFileContent");
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFilesAndCommit() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + currentLocalFileName)).exists();

        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        request.put(testRepoBaseUrl + auxiliaryRepository.getId() + "/files?commit=true", getFileSubmissions(), HttpStatus.OK);

        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

        Path filePath = Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + currentLocalFileName);
        assertThat(filePath).hasContent("updatedFileContent");

        var testRepoCommits = localAuxiliaryRepo.workingCopyCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getName()).isEqualTo(testRepoCommits.getFirst().getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void testSaveFiles_accessForbidden() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        // student1 should not have access to instructor1's tests repository even if they assume an INSTRUCTOR role.
        request.put(testRepoBaseUrl + auxiliaryRepository.getId() + "/files?commit=true", List.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFiles_conflict() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        Repository conflictedRepository = createMergeConflictInServerClone();
        try {
            request.put(testRepoBaseUrl + auxiliaryRepository.getId() + "/files?commit=true", List.of(), HttpStatus.CONFLICT);
        }
        finally {
            deleteLocalClone(conflictedRepository);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFiles_serviceUnavailable() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        deleteRemoteAuxiliaryRepository();
        request.put(testRepoBaseUrl + auxiliaryRepository.getId() + "/files?commit=true", List.of(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPullChanges() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        String fileName = "remoteFile";

        // Create a commit for the local
        request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/commit", null, HttpStatus.OK, null);
        try (var remoteRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(localAuxiliaryRepo.bareRepositoryPath(), null)) {

            // Create file in the remote repository
            Path filePath = Path.of(localAuxiliaryRepo.bareRepositoryPath().toFile() + "/" + fileName);
            Files.createFile(filePath);

            // Check if the file exists in the remote repository and that it doesn't yet exist in the local repository
            assertThat(Path.of(localAuxiliaryRepo.bareRepositoryPath().toFile() + "/" + fileName)).exists();
            assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + fileName)).doesNotExist();

            // Stage all changes and make a second commit in the remote repository
            gitService.stageAllChanges(remoteRepository);
            GitService.commit(localAuxiliaryRepo.bareRepository()).setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();

            // Checks if the current commit is not equal on the local and the remote repository
            assertThat(localAuxiliaryRepo.workingCopyCommits().getFirst()).isNotEqualTo(localAuxiliaryRepo.bareRepositoryCommits().getFirst());

            // Execute the Rest call
            request.get(testRepoBaseUrl + auxiliaryRepository.getId() + "/pull", HttpStatus.OK, Void.class);

            // Check if the current commit is the same on the local and the remote repository and if the file exists on the local repository
            assertThat(localAuxiliaryRepo.workingCopyCommits().getFirst()).isEqualTo(localAuxiliaryRepo.bareRepositoryCommits().getFirst());
            assertThat(Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + fileName)).exists();
        }
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetToLastCommit() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        String fileName = "testFile";
        try (var localRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(localAuxiliaryRepo.workingCopyPath(), null);
                var remoteRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(localAuxiliaryRepo.bareRepositoryPath(), null)) {

            // Check status of git before the commit
            var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

            // Create a commit for the local and the remote repository
            request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/commit", null, HttpStatus.OK, null);

            // Check status of git after the commit
            var receivedStatusAfterCommit = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

            // Create file in the local repository and commit it
            Path localFilePath = Path.of(localAuxiliaryRepo.workingCopyPath().toFile() + "/" + fileName);
            var localFile = Files.createFile(localFilePath).toFile();
            // write content to the created file
            FileUtils.write(localFile, "local", Charset.defaultCharset());
            gitService.stageAllChanges(localRepo);
            GitService.commit(localAuxiliaryRepo.workingCopy()).setMessage("local").call();

            // Create file in the remote repository and commit it
            Path remoteFilePath = Path.of(localAuxiliaryRepo.bareRepositoryPath().toFile() + "/" + fileName);
            var remoteFile = Files.createFile(remoteFilePath).toFile();
            // write content to the created file
            FileUtils.write(remoteFile, "remote", Charset.defaultCharset());
            gitService.stageAllChanges(remoteRepo);
            GitService.commit(localAuxiliaryRepo.bareRepository()).setMessage("remote").call();

            // Merge the two and a conflict will occur
            localAuxiliaryRepo.workingCopy().fetch().setRemote("origin").call();
            List<Ref> refs = localAuxiliaryRepo.workingCopy().branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            var result = localAuxiliaryRepo.workingCopy().merge().include(refs.getFirst().getObjectId()).setStrategy(MergeStrategy.RESOLVE).call();
            var status = localAuxiliaryRepo.workingCopy().status().call();
            assertThat(status.getConflicting()).isNotEmpty();
            assertThat(result.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);

            // Execute the reset Rest call
            request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/reset", null, HttpStatus.OK, null);

            // Check the git status after the reset
            status = localAuxiliaryRepo.workingCopy().status().call();
            assertThat(status.getConflicting()).isEmpty();
            assertThat(localAuxiliaryRepo.workingCopyCommits().getFirst()).isEqualTo(localAuxiliaryRepo.bareRepositoryCommits().getFirst());
            var receivedStatusAfterReset = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusAfterReset.repositoryStatus()).hasToString("CLEAN");
        }
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStatus() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);

        // The current status is "uncommited changes", since we added files and folders, but we didn't commit yet
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        // Perform a commit to check if the status changes
        request.postWithoutLocation(testRepoBaseUrl + auxiliaryRepository.getId() + "/commit", null, HttpStatus.OK, null);

        // Check if the status of git is "clean" after the commit
        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void testGetStatus_cannotAccessRepository() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        // student1 should not have access to instructor1's tests repository even if they assume the role of an INSTRUCTOR.
        request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.FORBIDDEN, RepositoryStatusDTO.class);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIsClean() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        var status = request.get(testRepoBaseUrl + auxiliaryRepository.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(status).isNotNull();
        assertThat(status.repositoryStatus()).isEqualTo(RepositoryStatusDTOType.CLEAN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void handleAuxiliaryRepositoriesWhenUpdatingExercises_createsTheRepositoryOfANewlyAddedAuxiliaryRepository() {
        // An auxiliary repository without an id is one the instructor just added in the update form, so its repository does not exist yet.
        var added = new AuxiliaryRepository();
        added.setName("newaux");
        added.setCheckoutDirectory("newaux");
        var updatedExercise = new ProgrammingExercise();
        updatedExercise.setId(programmingExercise.getId());
        updatedExercise.setProgrammingLanguage(programmingExercise.getProgrammingLanguage());
        ReflectionTestUtils.setField(updatedExercise, "projectKey", programmingExercise.getProjectKey());
        updatedExercise.setAuxiliaryRepositories(new ArrayList<>(List.of(added)));

        var exerciseBeforeUpdate = new ProgrammingExercise();
        exerciseBeforeUpdate.setId(programmingExercise.getId());
        exerciseBeforeUpdate.setAuxiliaryRepositories(new ArrayList<>());

        programmingExerciseRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(exerciseBeforeUpdate, updatedExercise);

        assertThat(added.getRepositoryUri()).as("the new auxiliary repository is given a URI").isNotBlank();
        Path createdRepository = new LocalVCRepositoryUri(added.getRepositoryUri()).getLocalRepositoryPath(localVCBasePath);
        assertThat(createdRepository).as("the repository is created in the LocalVC folder structure").isDirectory();
        assertThat(createdRepository.resolve("HEAD")).as("it is a bare repository").isRegularFile();
        assertThat(gitService.isBareRepositoryHealthy(new LocalVCRepositoryUri(added.getRepositoryUri()))).as("it received an initial commit, so it has a branch").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void handleAuxiliaryRepositoriesWhenUpdatingExercises_deletesTheRepositoryOfARemovedAuxiliaryRepository() {
        Path existingRepository = auxRepoUri.getLocalRepositoryPath(localVCBasePath);
        assertThat(existingRepository).as("the auxiliary repository exists before the update").isDirectory();

        // The updated exercise no longer lists the auxiliary repository, so its repository has to be removed from version control.
        var exerciseBeforeUpdate = new ProgrammingExercise();
        exerciseBeforeUpdate.setId(programmingExercise.getId());
        exerciseBeforeUpdate.setAuxiliaryRepositories(new ArrayList<>(List.of(auxiliaryRepository)));
        var updatedExercise = new ProgrammingExercise();
        updatedExercise.setId(programmingExercise.getId());
        updatedExercise.setAuxiliaryRepositories(new ArrayList<>());

        programmingExerciseRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(exerciseBeforeUpdate, updatedExercise);

        assertThat(existingRepository).as("the repository of the removed auxiliary repository is deleted").doesNotExist();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void handleAuxiliaryRepositoriesWhenUpdatingExercises_leavesUnchangedAuxiliaryRepositoriesAlone() {
        Path existingRepository = auxRepoUri.getLocalRepositoryPath(localVCBasePath);

        // The same auxiliary repository is present before and after, so nothing is created and nothing is deleted.
        var exerciseBeforeUpdate = new ProgrammingExercise();
        exerciseBeforeUpdate.setId(programmingExercise.getId());
        exerciseBeforeUpdate.setAuxiliaryRepositories(new ArrayList<>(List.of(auxiliaryRepository)));
        var updatedExercise = new ProgrammingExercise();
        updatedExercise.setId(programmingExercise.getId());
        updatedExercise.setAuxiliaryRepositories(new ArrayList<>(List.of(auxiliaryRepository)));

        programmingExerciseRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(exerciseBeforeUpdate, updatedExercise);

        assertThat(existingRepository).as("an auxiliary repository that stayed is untouched").isDirectory();
    }

}
