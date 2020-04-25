package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
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
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

public class TestRepositoryResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    private Course course;

    private ProgrammingExercise exercise;

    private final static int numberOfStudents = 2;

    public class LocalRepoTest {

        File localRepoFile;

        File originRepoFile;

        Git localGit;

        Git originGit;

        void configureRepos(String localRepoFileName, String originRepoFileName) throws Exception {

            this.localRepoFile = Files.createTempDirectory(localRepoFileName).toFile();
            Path filePath = Paths.get(localRepoFile + "/test");
            var file = Files.createFile(filePath).toFile();
            FileUtils.write(file, "awesome");
            this.localGit = Git.init().setDirectory(localRepoFile).call();

            this.originRepoFile = Files.createTempDirectory(originRepoFileName).toFile();
            this.originGit = Git.init().setDirectory(originRepoFile).call();

            StoredConfig config = this.localGit.getRepository().getConfig();
            config.setString("remote", "origin", "url", this.originRepoFile.getAbsolutePath());
            config.save();
        }
    }

    LocalRepoTest testRepo = new LocalRepoTest();

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(numberOfStudents, 1, 1);
        course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        final var projectKey = exercise.getProjectKey();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        var testRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.originRepoFile);
        exercise.setTestRepositoryUrl(testRepoTestUrl.toString());
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoTestUrl.getURL(), true);
        doReturn(projectKey).when(versionControlService).getProjectKeyFromUrl(any());
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        resetLocalRepo(testRepo);
    }

    private void resetLocalRepo(LocalRepoTest localRepo) throws IOException {
        if (localRepo.localRepoFile != null && localRepo.localRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepo.localRepoFile);
        }
        if (localRepo.localGit != null) {
            localRepo.localGit.close();
        }

        if (localRepo.originRepoFile != null && localRepo.originRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepo.originRepoFile);
        }
        if (localRepo.originGit != null) {
            localRepo.originGit.close();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldGetFiles() throws Exception {
        programmingExerciseRepository.save(exercise);
        var files = request.getMap("/api/test-repository/" + exercise.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldGetFile() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "test");
        var file = request.get("/api/test-repository/" + exercise.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo("awesome");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldCreateFile() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFile"))).isFalse();
        params.add("file", "newFile");
        request.postWithoutResponseBody("/api/test-repository/" + exercise.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFile"))).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldCreateFolder() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFolder"))).isFalse();
        params.add("folder", "newFolder");
        request.postWithoutResponseBody("/api/test-repository/" + exercise.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/newFolder"))).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldDeleteFile() throws Exception {
        programmingExerciseRepository.save(exercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/test"))).isTrue();
        params.add("file", "test");
        request.delete("/api/test-repository/" + exercise.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.exists(Paths.get(testRepo.localRepoFile + "/test"))).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldCommitChanges() throws Exception {
        programmingExerciseRepository.save(exercise);
        var receivedStatusBeforeCommit = request.get("/api/test-repository/" + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
        request.postWithoutLocation("/api/test-repository/" + exercise.getId() + "/commit", null, HttpStatus.OK, null);
        var receivedStatusAfterCommit = request.get("/api/test-repository/" + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldGetStatus() throws Exception {
        programmingExerciseRepository.save(exercise);
        var receivedStatus = request.get("/api/test-repository/" + exercise.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatus).isNotNull();
    }
}
