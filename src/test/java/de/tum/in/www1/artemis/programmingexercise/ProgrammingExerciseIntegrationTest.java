package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.websocket.dto.ProgrammingExerciseTestCaseStateDTO;

class ProgrammingExerciseIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    ProgrammingExercise programmingExercise;

    File downloadedFile;

    File repoFile;

    Git git;

    @BeforeEach
    void initTestCase() throws GitAPIException, InterruptedException, IOException {
        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");

        repoFile = Files.createTempDirectory("repo").toFile();
        git = Git.init().setDirectory(repoFile).call();

        // TODO use setupProgrammingExercise or setupTemplateAndPush to create actual content (based on the template repos) in this repository
        // so that e.g. addStudentIdToProjectName in ProgrammingExerciseExportService is tested properly as well

        // create one empty commit
        git.commit().setMessage("empty").setAllowEmpty(true).setAuthor("test", "test@test.com").call();
        var repository = gitService.getRepositoryByLocalPath(repoFile.toPath());
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(URL.class), anyBoolean(), anyString());
        doNothing().when(gitService).fetchAll(any());
        var objectId = git.reflog().call().iterator().next().getNewId();
        doReturn(objectId).when(gitService).getLastCommitHash(any());
    }

    @AfterEach
    void tearDown() throws IOException {
        database.resetDatabase();
        if (downloadedFile != null && downloadedFile.exists()) {
            FileUtils.forceDelete(downloadedFile);
        }
        if (repoFile != null && repoFile.exists()) {
            FileUtils.deleteDirectory(repoFile);
        }
        if (git != null) {
            git.close();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkIfProgrammingExerciseIsReleased_IsReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = database.addParticipationForExercise(programmingExercise, "student1");
        database.addResultToParticipation(participation);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.OK,
                ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.isReleased()).isTrue();
        assertThat(releaseStateDTO.isHasStudentResult()).isTrue();
        assertThat(releaseStateDTO.isTestCasesChanged()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkIfProgrammingExerciseIsReleased_IsNotReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = database.addParticipationForExercise(programmingExercise, "student1");
        database.addResultToParticipation(participation);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.OK,
                ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.isReleased()).isFalse();
        assertThat(releaseStateDTO.isHasStudentResult()).isTrue();
        assertThat(releaseStateDTO.isTestCasesChanged()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkIfProgrammingExerciseIsReleased_IsReleasedAndHasNoResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.OK,
                ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.isReleased()).isTrue();
        assertThat(releaseStateDTO.isHasStudentResult()).isFalse();
        assertThat(releaseStateDTO.isTestCasesChanged()).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void checkIfProgrammingExerciseIsReleased_forbidden() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.FORBIDDEN, Boolean.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void textExportSubmissionsByParticipationIds() throws Exception {
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString())
                .collect(Collectors.toList());
        final var path = ProgrammingExerciseResource.Endpoints.ROOT + ProgrammingExerciseResource.Endpoints.EXPORT_SUBMISSIONS_BY_PARTICIPATIONS
                .replace("{exerciseId}", "" + programmingExercise.getId()).replace("{participationIds}", String.join(",", participationIds));
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile.exists());
        // TODO: unzip the files and add some checks
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void textExportSubmissionsByStudentLogins() throws Exception {
        final var path = ProgrammingExerciseResource.Endpoints.ROOT + ProgrammingExerciseResource.Endpoints.EXPORT_SUBMISSIONS_BY_STUDENT
                .replace("{exerciseId}", "" + programmingExercise.getId()).replace("{studentIds}", "student1,student2");
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile.exists());
        // TODO: unzip the files and add some checks
    }

    private RepositoryExportOptionsDTO getOptions() {
        final var repositoryExportOptions = new RepositoryExportOptionsDTO();
        repositoryExportOptions.setFilterLateSubmissions(true);
        repositoryExportOptions.setCombineStudentCommits(true);
        repositoryExportOptions.setAddStudentName(true);
        repositoryExportOptions.setNormalizeCodeStyle(true);
        repositoryExportOptions.setFilterLateSubmissionsDate(ZonedDateTime.now());
        return repositoryExportOptions;
    }
}
