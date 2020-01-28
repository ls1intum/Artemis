package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints;
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

        // the following 2 lines prepare the generation of the structural test oracle
        var testjsonFilePath = Paths.get(repoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test.json");
        testjsonFilePath.toFile().getParentFile().mkdirs();

        // create two empty commits
        git.commit().setMessage("empty").setAllowEmpty(true).setAuthor("test", "test@test.com").call();
        git.commit().setMessage("empty").setAllowEmpty(true).setAuthor("test", "test@test.com").call();
        var repository = gitService.getRepositoryByLocalPath(repoFile.toPath());
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(URL.class), anyBoolean(), anyString());
        doNothing().when(gitService).fetchAll(any());
        var objectId = git.reflog().call().iterator().next().getNewId();
        doReturn(objectId).when(gitService).getLastCommitHash(any());
        doNothing().when(gitService).resetToOriginMaster(any());
        doNothing().when(gitService).pullIgnoreConflicts(any());
        doNothing().when(gitService).commitAndPush(any(), anyString(), any());

        doNothing().when(continuousIntegrationService).deleteBuildPlan(anyString(), anyString());
        doNothing().when(continuousIntegrationService).deleteProject(anyString());
        doNothing().when(versionControlService).deleteRepository(any(URL.class));
        doNothing().when(versionControlService).deleteProject(anyString());
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
    void textProgrammingExerciseIsReleased_IsReleasedAndHasResults() throws Exception {
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
    void textProgrammingExerciseIsReleased_IsNotReleasedAndHasResults() throws Exception {
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
    void textProgrammingExerciseIsReleased_forbidden() throws Exception {
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.FORBIDDEN, Boolean.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void textExportSubmissionsByParticipationIds() throws Exception {
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString())
                .collect(Collectors.toList());
        final var path = Endpoints.ROOT + Endpoints.EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", "" + programmingExercise.getId()).replace("{participationIds}",
                String.join(",", participationIds));
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile.exists());
        // TODO: unzip the files and add some checks
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void textExportSubmissionsByStudentLogins() throws Exception {
        final var path = Endpoints.ROOT
                + Endpoints.EXPORT_SUBMISSIONS_BY_STUDENT.replace("{exerciseId}", "" + programmingExercise.getId()).replace("{studentIds}", "student1,student2");
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile.exists());
        // TODO: unzip the files and add some checks
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete() throws Exception {
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE.replace("{exerciseId}", "" + programmingExercise.getId());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");
        request.delete(path, HttpStatus.OK, params);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExercise() throws Exception {
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE.replace("{exerciseId}", "" + programmingExercise.getId());
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        // TODO add more assertions
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations() throws Exception {
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "instructor1");
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", "" + programmingExercise.getId());
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        assertThat(programmingExerciseServer.getStudentParticipations()).isNotEmpty();
        assertThat(programmingExerciseServer.getTemplateParticipation()).isNotNull();
        assertThat(programmingExerciseServer.getSolutionParticipation()).isNotNull();
        // TODO add more assertions
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExercisesForCourse() throws Exception {
        final var path = Endpoints.ROOT + Endpoints.GET_FOR_COURSE.replace("{courseId}", "" + programmingExercise.getCourse().getId());
        var programmingExercisesServer = request.getList(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExercisesServer).isNotEmpty();
        // TODO add more assertions
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGenerateStructureOracle() throws Exception {
        final var path = Endpoints.ROOT + Endpoints.GENERATE_TESTS.replace("{exerciseId}", "" + programmingExercise.getId());
        var result = request.get(path, HttpStatus.OK, String.class);
        assertThat(result).startsWith("Successfully generated the structure oracle");
        request.get(path, HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCombineTemplateRepositoryCommits() throws Exception {
        final var path = Endpoints.ROOT + Endpoints.COMBINE_COMMITS.replace("{exerciseId}", "" + programmingExercise.getId());
        request.put(path, Void.class, HttpStatus.OK);
        // TODO add more assertions, when we use actual git repos
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
