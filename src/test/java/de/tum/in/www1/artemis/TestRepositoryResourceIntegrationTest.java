package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

@TestPropertySource(properties = "artemis.repo-clone-path=")
public class TestRepositoryResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final String testRepoBaseUrl = "/api/test-repository/";

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    private Course course;

    private ProgrammingExercise exercise;

    private String currentLocalFileName = "currentFileName";

    private String currentLocalFileContent = "testContent";

    private String currentLocalFolderName = "currentFolderName";

    private String newLocalFileName = "newFileName";

    private String newLocalFolderName = "newFolderName";

    LocalRepository testRepo = new LocalRepository();

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(0, 0, 1);
        course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");

        // add file to the repository folder
        Path filePath = Paths.get(testRepo.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent);

        // add folder to the repository folder
        filePath = Paths.get(testRepo.localRepoFile + "/" + currentLocalFolderName);
        var folder = Files.createDirectory(filePath).toFile();

        var testRepoUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.localRepoFile);
        exercise.setTestRepositoryUrl(testRepoUrl.toString());
        doReturn(gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoUrl.getURL(), true);
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        LocalRepository.resetLocalRepo(testRepo);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldGetFiles() throws Exception {
        programmingExerciseRepository.save(exercise);
        var files = request.getMap(testRepoBaseUrl + exercise.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();
        for (String key : files.keySet()) {
            assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + key))).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldGetFile() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(testRepoBaseUrl + exercise.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldCreateFile() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFile"))).isFalse();
        params.add("file", "newFile");
        request.postWithoutResponseBody(testRepoBaseUrl + exercise.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.isRegularFile(Paths.get(testRepo.localRepoFile + "/newFile"))).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldCreateFolder() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFolder"))).isFalse();
        params.add("folder", "newFolder");
        request.postWithoutResponseBody(testRepoBaseUrl + exercise.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Files.isDirectory(Paths.get(testRepo.localRepoFile + "/newFolder"))).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldRenameFile() throws Exception {
        programmingExerciseRepository.save(exercise);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + currentLocalFileName))).isTrue();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + newLocalFileName))).isFalse();
        FileMove fileMove = new FileMove();
        fileMove.setCurrentFilePath(currentLocalFileName);
        fileMove.setNewFilename(newLocalFileName);
        request.postWithoutLocation(testRepoBaseUrl + exercise.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + currentLocalFileName))).isFalse();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + newLocalFileName))).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldRenameFolder() throws Exception {
        programmingExerciseRepository.save(exercise);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + currentLocalFolderName))).isTrue();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + newLocalFolderName))).isFalse();
        FileMove fileMove = new FileMove();
        fileMove.setCurrentFilePath(currentLocalFolderName);
        fileMove.setNewFilename(newLocalFolderName);
        request.postWithoutLocation(testRepoBaseUrl + exercise.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + currentLocalFolderName))).isFalse();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + newLocalFolderName))).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldDeleteFile() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + currentLocalFileName))).isTrue();
        params.add("file", currentLocalFileName);
        request.delete(testRepoBaseUrl + exercise.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + currentLocalFileName))).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldCommitChanges() throws Exception {
        programmingExerciseRepository.save(exercise);
        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(testRepoBaseUrl + exercise.getId() + "/commit", null, HttpStatus.OK, null);
        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");
        var testRepoCommits = LocalRepository.getAllCommits(testRepo.localGit);
        assertThat(testRepoCommits.size() == 1).isTrue();
        assertThat(database.getUserByLogin("instructor1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldPullChanges() throws Exception {
        programmingExerciseRepository.save(exercise);
        request.get(testRepoBaseUrl + exercise.getId() + "/pull", HttpStatus.OK, Void.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldResetToLastCommit() throws Exception {
        programmingExerciseRepository.save(exercise);
        String fileName = "change";

        // Check status of git before the commit
        var receivedStatusBeforeCommit = request.get(testRepoBaseUrl + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(testRepoBaseUrl + exercise.getId() + "/commit", null, HttpStatus.OK, null);
        var localRepo = gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath());

        // Create a second commit for the local repository
        gitService.stageAllChanges(localRepo);
        testRepo.localGit.commit().setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();

        // Check status of git after the commit
        var receivedStatusAfterCommit = request.get(testRepoBaseUrl + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");

        // Create a new file
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", fileName);
        request.postWithoutResponseBody(testRepoBaseUrl + exercise.getId() + "/file", HttpStatus.OK, params);

        // Check if file exist
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + fileName))).isTrue();

        // Checks status of git after the file creation and before the reset
        var receivedStatusBeforeReset = request.get(testRepoBaseUrl + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeReset.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");

        // Stages the new file, otherwise the reset will not delete it
        gitService.stageAllChanges(localRepo);

        // Executes the reset Rest call
        request.postWithoutLocation(testRepoBaseUrl + exercise.getId() + "/reset", null, HttpStatus.OK, null);
        ;

        // Checks if the file still exists after the reset
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/" + fileName))).isFalse();

        // Checks the git status after the reset
        var receivedStatusAfterReset = request.get(testRepoBaseUrl + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterReset.repositoryStatus.toString()).isEqualTo("CLEAN");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldGetStatus() throws Exception {
        programmingExerciseRepository.save(exercise);
        var receivedStatus = request.get(testRepoBaseUrl + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatus).isNotNull();
    }
}
