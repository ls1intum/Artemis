package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

public class RepositoryProgrammingExerciseParticipationResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final String studentRepoBaseUrl = "/api/repository/";

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    private ProgrammingExercise programmingExercise;

    private String currentLocalFileName = "currentFileName";

    private String currentLocalFileContent = "testContent";

    private String currentLocalFolderName = "currentFolderName";

    private String newLocalFileName = "newFileName";

    private String newLocalFolderName = "newFolderName";

    LocalRepository studentRepository = new LocalRepository();

    List<BuildLogEntry> logs = new ArrayList<>();

    BuildLogEntry buildLogEntry = new BuildLogEntry(ZonedDateTime.now(), "Checkout to revision e65aa77cc0380aeb9567ccceb78aca416d86085b has failed.");

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(1, 0, 0);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);

        studentRepository.configureRepos("studentLocalRepo", "studentOriginRepo");

        // add file to the repository folder
        Path filePath = Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();

        // write content to the created file
        FileUtils.write(file, currentLocalFileContent);

        // add folder to the repository folder
        filePath = Paths.get(studentRepository.localRepoFile + "/" + currentLocalFolderName);
        var folder = Files.createDirectory(filePath).toFile();

        var localRepoUrl = new GitUtilService.MockFileRepositoryUrl(studentRepository.localRepoFile);
        database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, "student1", localRepoUrl.getURL());
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        programmingExercise.setTestRepositoryUrl(localRepoUrl.toString());
        doReturn(gitService.getRepositoryByLocalPath(studentRepository.localRepoFile.toPath())).when(gitService)
                .getOrCheckoutRepository(((ProgrammingExerciseParticipation) participation).getRepositoryUrlAsUrl(), true);

        doReturn(gitService.getRepositoryByLocalPath(studentRepository.localRepoFile.toPath())).when(gitService)
                .getOrCheckoutRepository(((ProgrammingExerciseParticipation) participation).getRepositoryUrlAsUrl(), false);

        logs.add(buildLogEntry);
        doReturn(logs).when(continuousIntegrationService).getLatestBuildLogs(programmingExercise.getProjectKey(),
                ((ProgrammingExerciseParticipation) participation).getBuildPlanId());
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        studentRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldGetFiles() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + key))).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldGetFile() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldCreateFile() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "newFile");
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/newFile"))).isFalse();
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.isRegularFile(Paths.get(studentRepository.localRepoFile + "/newFile"))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldCreateFolder() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("folder", "newFolder");
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/newFolder"))).isFalse();
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Files.isDirectory(Paths.get(studentRepository.localRepoFile + "/newFolder"))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldRenameFile() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + newLocalFileName))).isFalse();
        FileMove fileMove = new FileMove();
        fileMove.setCurrentFilePath(currentLocalFileName);
        fileMove.setNewFilename(newLocalFileName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isFalse();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + newLocalFileName))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldRenameFolder() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFolderName))).isTrue();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + newLocalFolderName))).isFalse();
        FileMove fileMove = new FileMove();
        fileMove.setCurrentFilePath(currentLocalFolderName);
        fileMove.setNewFilename(newLocalFolderName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFolderName))).isFalse();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + newLocalFolderName))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldDeleteFile() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        request.delete(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldCommitChanges() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");
        var testRepoCommits = studentRepository.getAllLocalCommits();
        assertThat(testRepoCommits.size() == 1).isTrue();
        assertThat(database.getUserByLogin("student1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldPullChanges() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);

        String fileName = "remoteFile";

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        var remote = gitService.getRepositoryByLocalPath(studentRepository.originRepoFile.toPath());

        // Create file in the remote repository
        Path filePath = Paths.get(studentRepository.originRepoFile + "/" + fileName);
        Files.createFile(filePath).toFile();

        // Check if the file exists in the remote repository and that it doesn't yet exists in the local repository
        assertThat(Files.exists(Paths.get(studentRepository.originRepoFile + "/" + fileName))).isTrue();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + fileName))).isFalse();

        // Stage all changes and make a second commit in the remote repository
        gitService.stageAllChanges(remote);
        studentRepository.originGit.commit().setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();

        // Checks if the current commit is not equal on the local and the remote repository
        assertThat(studentRepository.getAllLocalCommits().get(0)).isNotEqualTo(studentRepository.getAllOriginCommits().get(0));

        // Execute the Rest call
        request.get(studentRepoBaseUrl + participation.getId() + "/pull", HttpStatus.OK, Void.class);

        // Check if the current commit is the same on the local and the remote repository and if the file exists on the local repository
        assertThat(studentRepository.getAllLocalCommits().get(0)).isEqualTo(studentRepository.getAllOriginCommits().get(0));
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + fileName))).isTrue();

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldResetToLastCommit() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        String fileName = "change";

        // Check status of git before the commit
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        var localRepo = gitService.getRepositoryByLocalPath(studentRepository.localRepoFile.toPath());

        // Stage all files
        gitService.stageAllChanges(localRepo);

        // Check status of git after the commit
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");

        // Create a new file
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", fileName);
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);

        // Check if file exist
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + fileName))).isTrue();

        // Checks status of git after the file creation and before the reset
        var receivedStatusBeforeReset = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeReset.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");

        // Stages the new file, otherwise the reset will not delete it
        gitService.stageAllChanges(localRepo);

        // Executes the reset Rest call
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/reset", null, HttpStatus.OK, null);

        // Checks if the file still exists after the reset
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + fileName))).isFalse();

        // Checks the git status after the reset
        var receivedStatusAfterReset = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterReset.repositoryStatus.toString()).isEqualTo("CLEAN");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldGetStatus() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        var receivedStatus = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatus).isNotNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldGetResultDetails() throws Exception {
        StudentParticipation participation = studentParticipationRepository.findAll().get(0);
        var receivedLogs = request.get(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs).hasSize(1);
    }

}
