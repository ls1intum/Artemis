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
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

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

    private final static int numberOfStudents = 2;

    private String currentLocalFileName = "currentFileName";

    private String currentLocalFileContent = "testContent";

    private String newLocalFileName = "newFileName";

    LocalRepository testRepo = new LocalRepository();

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(numberOfStudents, 1, 1);
        course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");

        // add file to the repository folder
        Path filePath = Paths.get(testRepo.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent);

        var testRepoUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.originRepoFile);
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
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFile"))).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldCreateFolder() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFolder"))).isFalse();
        params.add("folder", "newFolder");
        request.postWithoutResponseBody(testRepoBaseUrl + exercise.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFolder"))).isTrue();
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
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldPullChanges() throws Exception {
        programmingExerciseRepository.save(exercise);
        request.get(testRepoBaseUrl + exercise.getId() + "/pull", HttpStatus.OK, Void.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldGetStatus() throws Exception {
        programmingExerciseRepository.save(exercise);
        var receivedStatus = request.get(testRepoBaseUrl + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatus).isNotNull();
    }
}
