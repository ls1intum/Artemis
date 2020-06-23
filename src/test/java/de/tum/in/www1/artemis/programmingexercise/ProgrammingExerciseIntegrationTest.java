package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.*;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.ErrorKeys.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.Verifiable;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseTestCaseResource;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.websocket.dto.ProgrammingExerciseTestCaseStateDTO;

class ProgrammingExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    GitUtilService gitUtilService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    ProgrammingExercise programmingExercise;

    ProgrammingExercise programmingExerciseInExam;

    File downloadedFile;

    File localRepoFile;

    File originRepoFile;

    Git localGit;

    Git remoteGit;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
        programmingExerciseInExam = database.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();

        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");

        database.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, "student2");

        localRepoFile = Files.createTempDirectory("repo").toFile();
        localGit = Git.init().setDirectory(localRepoFile).call();

        originRepoFile = Files.createTempDirectory("repoOrigin").toFile();
        remoteGit = Git.init().setDirectory(originRepoFile).call();
        StoredConfig config = localGit.getRepository().getConfig();
        config.setString("remote", "origin", "url", originRepoFile.getAbsolutePath());
        config.save();

        // TODO use setupProgrammingExercise or setupTemplateAndPush to create actual content (based on the template repos) in this repository
        // so that e.g. addStudentIdToProjectName in ProgrammingExerciseExportService is tested properly as well

        // the following 2 lines prepare the generation of the structural test oracle
        var testjsonFilePath = Paths.get(localRepoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test.json");
        gitUtilService.writeEmptyJsonFileToPath(testjsonFilePath);
        // create two empty commits
        localGit.commit().setMessage("empty").setAllowEmpty(true).setAuthor("test", "test@test.com").call();
        localGit.push().call();

        // we use the temp repository as remote origing for all repositories that are created during the
        // TODO: distinguish between template, test and solution
        doReturn(new GitUtilService.MockFileRepositoryUrl(originRepoFile)).when(versionControlService).getCloneRepositoryUrl(anyString(), anyString());
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws IOException {
        database.resetDatabase();
        if (downloadedFile != null && downloadedFile.exists()) {
            FileUtils.forceDelete(downloadedFile);
        }
        if (localRepoFile != null && localRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepoFile);
        }
        if (localGit != null) {
            localGit.close();
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
        var repository = gitService.getRepositoryByLocalPath(localRepoFile.toPath());
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(URL.class), anyBoolean(), anyString());
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
    void textExportSubmissionsByParticipationIds_invalidParticipationId_badRequest() throws Exception {
        final var path = Endpoints.ROOT
                + Endpoints.EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", "" + programmingExercise.getId()).replace("{participationIds}", "" + 10L);
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void textExportSubmissionsByParticipationIds_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString())
                .collect(Collectors.toList());
        final var path = Endpoints.ROOT + Endpoints.EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", "" + programmingExercise.getId()).replace("{participationIds}",
                String.join(",", participationIds));
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void textExportSubmissionsByStudentLogins() throws Exception {
        var repository = gitService.getRepositoryByLocalPath(localRepoFile.toPath());
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(URL.class), anyBoolean(), anyString());
        final var path = Endpoints.ROOT
                + Endpoints.EXPORT_SUBMISSIONS_BY_PARTICIPANTS.replace("{exerciseId}", "" + programmingExercise.getId()).replace("{participantIdentifiers}", "student1,student2");
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile.exists());
        // TODO: unzip the files and add some checks
    }

    private RepositoryExportOptionsDTO getOptions() {
        final var repositoryExportOptions = new RepositoryExportOptionsDTO();
        repositoryExportOptions.setFilterLateSubmissions(true);
        repositoryExportOptions.setCombineStudentCommits(true);
        repositoryExportOptions.setAddParticipantName(true);
        repositoryExportOptions.setNormalizeCodeStyle(true);
        return repositoryExportOptions;
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete() throws Exception {
        final var verifiables = new LinkedList<Verifiable>();
        final var projectKey = programmingExercise.getProjectKey();
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE.replace("{exerciseId}", "" + programmingExercise.getId());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        verifiables.add(bambooRequestMockProvider.mockDeleteProject(projectKey));
        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            verifiables.add(bambooRequestMockProvider.mockDeletePlan(projectKey + "-" + planName.toUpperCase()));
        }
        for (final var repoName : List.of("student1", "student2", RepositoryType.TEMPLATE.getName(), RepositoryType.SOLUTION.getName(), RepositoryType.TESTS.getName())) {
            bitbucketRequestMockProvider.mockDeleteRepository(projectKey, (projectKey + "-" + repoName).toLowerCase());
        }
        bitbucketRequestMockProvider.mockDeleteProject(projectKey);

        request.delete(path, HttpStatus.OK, params);

        for (final var verifiable : verifiables) {
            verifiable.performVerification();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE.replace("{exerciseId}", "" + programmingExercise.getId());
        request.delete(path, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE.replace("{exerciseId}", "" + programmingExercise.getId());
        request.delete(path, HttpStatus.FORBIDDEN);
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
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE.replace("{exerciseId}", "" + programmingExercise.getId());
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
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
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", "" + programmingExercise.getId());
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var path = Endpoints.ROOT + Endpoints.PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", "" + programmingExercise.getId());
        request.get(path, HttpStatus.NOT_FOUND, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExercisesForCourse() throws Exception {
        final var path = Endpoints.ROOT + Endpoints.GET_FOR_COURSE.replace("{courseId}", "" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        var programmingExercisesServer = request.getList(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExercisesServer).isNotEmpty();
        // TODO add more assertions
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExercisesForCourse_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = Endpoints.ROOT + Endpoints.GET_FOR_COURSE.replace("{courseId}", "" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        request.getList(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGenerateStructureOracle() throws Exception {
        var repository = gitService.getRepositoryByLocalPath(localRepoFile.toPath());
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(URL.class), anyBoolean(), anyString());
        final var path = Endpoints.ROOT + Endpoints.GENERATE_TESTS.replace("{exerciseId}", "" + programmingExercise.getId());
        var result = request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.OK);
        assertThat(result).startsWith("Successfully generated the structure oracle");
        request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_invalidTemplateBuildPlan_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        bambooRequestMockProvider.mockBuildPlanIsValid(programmingExercise.getTemplateBuildPlanId(), false);
        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_BUILD_PLAN_ID);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_idIsNull_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise.setId(null);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_invalidTemplateVcs_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.mockBuildPlanIsValid(programmingExercise.getTemplateBuildPlanId(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getTemplateRepositoryUrlAsUrl(), programmingExercise.getProjectKey(), false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_REPOSITORY_URL);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_invalidSolutionBuildPlan_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.mockBuildPlanIsValid(programmingExercise.getTemplateBuildPlanId(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getTemplateRepositoryUrlAsUrl(), programmingExercise.getProjectKey(), true);
        bambooRequestMockProvider.mockBuildPlanIsValid(programmingExercise.getSolutionBuildPlanId(), false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_BUILD_PLAN_ID);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_invalidSolutionRepository_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.mockBuildPlanIsValid(programmingExercise.getTemplateBuildPlanId(), true);
        bambooRequestMockProvider.mockBuildPlanIsValid(programmingExercise.getSolutionBuildPlanId(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getTemplateRepositoryUrlAsUrl(), programmingExercise.getProjectKey(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getSolutionRepositoryUrlAsUrl(), programmingExercise.getProjectKey(), false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_REPOSITORY_URL);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void updateProblemStatement_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var endpoint = "/api" + ProgrammingExerciseResource.Endpoints.PROBLEM.replace("{exerciseId}", programmingExercise.getId() + "");
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.FORBIDDEN, MediaType.TEXT_PLAIN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProblemStatement_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var endpoint = "/api" + ProgrammingExerciseResource.Endpoints.PROBLEM.replace("{exerciseId}", programmingExercise.getId() + "");
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.NOT_FOUND, MediaType.TEXT_PLAIN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_exerciseIsNull_badRequest() throws Exception {
        request.post(ROOT + SETUP, null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_idIsNotNull_badRequest() throws Exception {
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post(ROOT + SETUP, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        programmingExercise.setId(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_titleNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_titleContainsBadCharacter_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("abc?=§ ``+##");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_invalidShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExercise.setShortName("hi");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExerciseInExam.setId(null);
        programmingExercise.setId(null);
        request.post(ROOT + SETUP, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_shortNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("asdb ³¼²½¼³`` ");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_noProgrammingLanguageSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        programmingExercise.setProgrammingLanguage(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_packageNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("..asd. ß?");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_packageNameContainsKeyword_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("abc.final.xyz");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_packageNameElementBeginsWithDigit_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("eist.2020something");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_packageNameIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName(null);
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_maxScoreIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setMaxScore(null);
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_vcsProjectAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockCheckIfProjectExists(programmingExercise, true);

        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void setupProgrammingExercise_bambooProjectAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockCheckIfProjectExists(programmingExercise, false);
        bambooRequestMockProvider.mockCheckIfProjectExists(programmingExercise, true);

        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId() + ""), programmingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_templateIDDoesnotExist_notFound() throws Exception {
        programmingExercise.setShortName("newShortName");
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExerciseInExam.setId(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void exportSubmissionsByStudentLogins_notInstructorForExercise_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.post(getDefaultAPIEndpointForExportRepos(), getOptions(), HttpStatus.FORBIDDEN);
    }

    @NotNull
    private String getDefaultAPIEndpointForExportRepos() {
        return ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPANTS.replace("{exerciseId}", programmingExercise.getId() + "").replace("{participantIdentifiers}", "1,2,3");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void exportSubmissionsByStudentLogins_exportAllAsTutor_forbidden() throws Exception {
        final var options = getOptions();
        options.setExportAllParticipants(true);
        request.post(getDefaultAPIEndpointForExportRepos(), options, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void generateStructureOracleForExercise_exerciseDoesNotExist_badRequest() throws Exception {
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", programmingExercise.getId() + 1337 + ""), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void generateStructureOracleForExercise_userIsNotAdminInCourse_badRequest() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", programmingExercise.getId() + ""), programmingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void generateStructureOracleForExercise_invalidPackageName_badRequest() throws Exception {
        programmingExercise.setPackageName(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", programmingExercise.getId() + ""), programmingExercise, HttpStatus.BAD_REQUEST);

        programmingExercise.setPackageName("ab");
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", programmingExercise.getId() + ""), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void hasAtLeastOneStudentResult_exerciseDoesNotExist_notFound() throws Exception {
        request.get(ROOT + TEST_CASE_STATE.replace("{exerciseId}", programmingExercise.getId() + 1337 + ""), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "tutoralt1", roles = "TA")
    public void hasAtLeastOneStudentResult_isNotTeachingAssistant_forbidden() throws Exception {
        database.addTeachingAssistant("other-tutors", "tutoralt");
        request.get(ROOT + TEST_CASE_STATE.replace("{exerciseId}", programmingExercise.getId() + ""), HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getTestCases_asTutor() throws Exception {
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", programmingExercise.getId() + "");
        final var returnedTests = request.getList(ROOT + endpoint, HttpStatus.OK, ProgrammingExerciseTestCase.class);
        final var testsInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        returnedTests.forEach(testCase -> testCase.setExercise(programmingExercise));

        assertThat(new HashSet<>(returnedTests)).isEqualTo(testsInDB);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void getTestCases_asStudent_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", programmingExercise.getId() + "");
        request.getList(ROOT + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    @Test
    @WithMockUser(username = "other-teaching-assistant1", roles = "TA")
    public void getTestCases_tutorInOtherCourse_forbidden() throws Exception {
        database.addTeachingAssistant("other-teaching-assistants", "other-teaching-assistant");
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", programmingExercise.getId() + "");

        request.getList(ROOT + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateTestCases_asInstrutor() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExercise.getId()).get();
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> {
            final var testCaseUpdate = new ProgrammingExerciseTestCaseDTO();
            testCaseUpdate.setId(testCase.getId());
            testCaseUpdate.setAfterDueDate(true);
            testCaseUpdate.setWeight((int) (testCase.getId() + 42));
            return testCaseUpdate;
        }).collect(Collectors.toList());
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", programmingExercise.getId() + "");

        final var testCasesResponse = request.patchWithResponseBody(ROOT + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testCasesInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(new HashSet<>(testCasesResponse)).isEqualTo(testCasesInDB);
        assertThat(testCasesResponse).allSatisfy(testCase -> {
            assertThat(testCase.isAfterDueDate()).isTrue();
            assertThat(testCase.getWeight()).isEqualTo(testCase.getId() + 42);
        });
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateTestCases_nonExistingExercise_notFound() throws Exception {
        final var update = new ProgrammingExerciseTestCaseDTO();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", (programmingExercise.getId() + 1337) + "");
        request.patchWithResponseBody(ROOT + endpoint, List.of(update), String.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    public void updateTestCases_instructorInWrongCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "other-instructor");
        final var update = new ProgrammingExerciseTestCaseDTO();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", programmingExercise.getId() + "");

        request.patchWithResponseBody(ROOT + endpoint, List.of(update), String.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resetTestCaseWeights_asInstructor() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        programmingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(programmingExercise.getId()).get();
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.RESET_WEIGHTS.replace("{exerciseId}", programmingExercise.getId() + "");
        programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).forEach(test -> {
            test.setWeight(42);
            programmingExerciseTestCaseRepository.saveAndFlush(test);
        });

        final var testCasesResponse = request.patchWithResponseBody(ROOT + endpoint, "{}", new TypeReference<Set<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        final var testsInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(testCasesResponse).isEqualTo(testsInDB);
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getWeight()).isEqualTo(1));
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    public void resetTestCaseWeights_instructorInWrongCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "other-instructor");
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.RESET_WEIGHTS.replace("{exerciseId}", programmingExercise.getId() + "");
        request.patchWithResponseBody(ROOT + endpoint, "{}", String.class, HttpStatus.FORBIDDEN);
    }
}
