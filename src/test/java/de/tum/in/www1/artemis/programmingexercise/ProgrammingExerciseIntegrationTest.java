package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.*;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.ErrorKeys.INVALID_SOLUTION_BUILD_PLAN_ID;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.ErrorKeys.INVALID_SOLUTION_REPOSITORY_URL;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.ErrorKeys.INVALID_TEMPLATE_BUILD_PLAN_ID;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.ErrorKeys.INVALID_TEMPLATE_REPOSITORY_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseGradingResource;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseTestCaseResource;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.websocket.dto.ProgrammingExerciseTestCaseStateDTO;

class ProgrammingExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    GitUtilService gitUtilService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    NotificationRepository notificationRepository;

    Course course;

    ProgrammingExercise programmingExercise;

    ProgrammingExercise programmingExerciseInExam;

    ProgrammingExerciseStudentParticipation participation1;

    ProgrammingExerciseStudentParticipation participation2;

    File downloadedFile;

    File localRepoFile;

    File originRepoFile;

    Git localGit;

    Git remoteGit;

    File localRepoFile2;

    File originRepoFile2;

    Git localGit2;

    Git remoteGit2;

    private List<GradingCriterion> gradingCriteria;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(3, 2, 2);
        course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations().get(0);
        programmingExerciseInExam = database.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        programmingExerciseInExam = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseInExam.getId())
                .get();

        participation1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");

        database.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, "student2");

        localRepoFile = Files.createTempDirectory("repo").toFile();
        localGit = Git.init().setDirectory(localRepoFile).call();
        originRepoFile = Files.createTempDirectory("repoOrigin").toFile();
        remoteGit = Git.init().setDirectory(originRepoFile).call();
        StoredConfig config = localGit.getRepository().getConfig();
        config.setString("remote", "origin", "url", originRepoFile.getAbsolutePath());
        config.save();

        localRepoFile2 = Files.createTempDirectory("repo2").toFile();
        localGit2 = Git.init().setDirectory(localRepoFile2).call();
        originRepoFile2 = Files.createTempDirectory("repoOrigin").toFile();
        remoteGit2 = Git.init().setDirectory(originRepoFile2).call();
        StoredConfig config2 = localGit2.getRepository().getConfig();
        config2.setString("remote", "origin", "url", originRepoFile2.getAbsolutePath());
        config2.save();

        // TODO use createProgrammingExercise or setupTemplateAndPush to create actual content (based on the template repos) in this repository
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
        bambooRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
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
        bambooRequestMockProvider.reset();
        bitbucketRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void textProgrammingExerciseIsReleased_IsReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = database.createAndSaveParticipationForExercise(programmingExercise, "student1");
        database.addResultToParticipation(null, null, participation);

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
        StudentParticipation participation = database.createAndSaveParticipationForExercise(programmingExercise, "student1");
        database.addResultToParticipation(null, null, participation);

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
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile2.toPath(), null);
        doReturn(repository1).when(gitService).getOrCheckoutRepository(eq(participation1.getVcsRepositoryUrl()), anyString(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepository(eq(participation2.getVcsRepositoryUrl()), anyString(), anyBoolean());
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString())
                .collect(Collectors.toList());
        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}",
                String.join(",", participationIds));
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile.exists());
        // TODO: unzip the files and add some checks
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void textExportSubmissionsByParticipationIds_invalidParticipationId_badRequest() throws Exception {
        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}", "10");
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void textExportSubmissionsByParticipationIds_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString())
                .collect(Collectors.toList());
        final var path = ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participationIds}",
                String.join(",", participationIds));
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void textExportSubmissionsByStudentLogins() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile2.toPath(), null);
        doReturn(repository1).when(gitService).getOrCheckoutRepository(eq(participation1.getVcsRepositoryUrl()), anyString(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepository(eq(participation2.getVcsRepositoryUrl()), anyString(), anyBoolean());
        final var path = ROOT
                + EXPORT_SUBMISSIONS_BY_PARTICIPANTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participantIdentifiers}", "student1,student2");
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
        final var projectKey = programmingExercise.getProjectKey();
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            bambooRequestMockProvider.mockDeleteBambooBuildPlan(projectKey + "-" + planName.toUpperCase());
        }
        bambooRequestMockProvider.mockDeleteBambooBuildProject(projectKey);

        for (final var repoName : List.of("student1", "student2", RepositoryType.TEMPLATE.getName(), RepositoryType.SOLUTION.getName(), RepositoryType.TESTS.getName())) {
            bitbucketRequestMockProvider.mockDeleteRepository(projectKey, (projectKey + "-" + repoName).toLowerCase());
        }
        bitbucketRequestMockProvider.mockDeleteProject(projectKey);

        request.delete(path, HttpStatus.OK, params);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.delete(path, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.delete(path, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExercise() throws Exception {
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        // TODO add more assertions
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithStructuredGradingInstruction() throws Exception {
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());

        gradingCriteria = database.addGradingInstructionsToExercise(programmingExerciseServer);

        assertThat(programmingExerciseServer.getGradingCriteria().get(0).getTitle()).isEqualTo(null);
        assertThat(programmingExerciseServer.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().size()).isEqualTo(1);
        assertThat(gradingCriteria.get(1).getStructuredGradingInstructions().size()).isEqualTo(3);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ROOT + PROGRAMMING_EXERCISE.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations() throws Exception {
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "instructor1");
        final var path = ROOT + PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        assertThat(programmingExerciseServer.getStudentParticipations()).isNotEmpty();
        assertThat(programmingExerciseServer.getTemplateParticipation()).isNotNull();
        assertThat(programmingExerciseServer.getSolutionParticipation()).isNotNull();
        // TODO add more assertions
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetProgrammingExerciseWithJustTemplateAndSolutionParticipation() throws Exception {
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "tutor1");
        final var path = ROOT + PROGRAMMING_EXERCISE_WITH_TEMPLATE_AND_SOLUTION_PARTICIPATION.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        assertThat(programmingExerciseServer.getSolutionParticipation().getId()).isNotNull();
        assertThat(programmingExerciseServer.getTemplateParticipation().getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ROOT + PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var path = ROOT + PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.get(path, HttpStatus.NOT_FOUND, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExercisesForCourse() throws Exception {
        final var path = ROOT + GET_FOR_COURSE.replace("{courseId}", String.valueOf(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId()));
        var programmingExercisesServer = request.getList(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExercisesServer).isNotEmpty();
        // TODO add more assertions
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExercisesForCourse_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ROOT + GET_FOR_COURSE.replace("{courseId}", String.valueOf(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId()));
        request.getList(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGenerateStructureOracle() throws Exception {
        var repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(VcsRepositoryUrl.class), anyString(), anyBoolean());
        final var path = ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        var result = request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.OK);
        assertThat(result).startsWith("Successfully generated the structure oracle");
        request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_invalidTemplateBuildPlan_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getTemplateBuildPlanId(), false);
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
        // both values are not set --> bad request
        programmingExercise.setCourse(null);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
        // both values are set --> bad request
        programmingExerciseInExam.setCourse(course);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    private void mockBuildPlanAndRepositoryCheck(ProgrammingExercise programmingExercise) throws Exception {
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getTemplateBuildPlanId(), true);
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getSolutionBuildPlanId(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsSolutionRepositoryUrl(), programmingExercise.getProjectKey(), true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_staticCodeAnalysisMustNotChange_falseToTrue_badRequest() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_staticCodeAnalysisMustNotChange_trueToFalse_badRequest() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        request.put(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST);
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
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getTemplateBuildPlanId(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_REPOSITORY_URL);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_invalidSolutionBuildPlan_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getTemplateBuildPlanId(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getSolutionBuildPlanId(), false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_BUILD_PLAN_ID);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProgrammingExercise_invalidSolutionRepository_badRequest() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getTemplateBuildPlanId(), true);
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getSolutionBuildPlanId(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsSolutionRepositoryUrl(), programmingExercise.getProjectKey(), false);

        request.putAndExpectError(ROOT + PROGRAMMING_EXERCISES, programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_REPOSITORY_URL);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void updateProblemStatement_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var endpoint = "/api" + ProgrammingExerciseResource.Endpoints.PROBLEM.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.FORBIDDEN, MediaType.TEXT_PLAIN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateProblemStatement_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var endpoint = "/api" + ProgrammingExerciseResource.Endpoints.PROBLEM.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.NOT_FOUND, MediaType.TEXT_PLAIN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_exerciseIsNull_badRequest() throws Exception {
        request.post(ROOT + SETUP, null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_idIsNotNull_badRequest() throws Exception {
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post(ROOT + SETUP, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        programmingExercise.setId(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_titleNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_titleContainsBadCharacter_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("abc?=§ ``+##");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_invalidShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExercise.setShortName("hi");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_invalidCourseShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        course.setShortName(null);
        courseRepository.save(course);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
        course.setShortName("Hi");
        courseRepository.save(course);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExerciseInExam.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setId(null);
        request.post(ROOT + SETUP, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_shortNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("asdb ³¼²½¼³`` ");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_noProgrammingLanguageSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        programmingExercise.setProgrammingLanguage(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_packageNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("..asd. ß?");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_packageNameContainsKeyword_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("abc.final.xyz");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_packageNameElementBeginsWithDigit_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("eist.2020something");
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_packageNameIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName(null);
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_maxScoreIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setMaxScore(null);
        programmingExercise.setShortName("testShortName");
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise.setAllowOnlineEditor(false);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_staticCodeAnalysisAndSequential_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setSequentialTestRuns(true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_unsupportedProgrammingLanguageForStaticCodeAnalysis_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.programmingLanguage(ProgrammingLanguage.C);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_noStaticCodeAnalysisButMaxPenalty_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setMaxStaticCodeAnalysisPenalty(20);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_maxStaticCodePenaltyNegative_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setMaxStaticCodeAnalysisPenalty(-20);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockProjectKeyExists(programmingExercise);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_bambooProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockCheckIfProjectExists(programmingExercise, false);
        bambooRequestMockProvider.mockProjectKeyExists(programmingExercise);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockCheckIfProjectExists(programmingExercise, true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_bambooProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockCheckIfProjectExists(programmingExercise, false);
        bambooRequestMockProvider.mockCheckIfProjectExists(programmingExercise, true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_projectTypeMissing_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(null);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_projectTypeNotExpected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExercise.setProjectType(ProjectType.MAVEN);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @ParameterizedTest
    // It should fail for all ProgrammingExercises except Haskell
    @EnumSource(value = ProgrammingLanguage.class, names = { "HASKELL" }, mode = EnumSource.Mode.EXCLUDE)
    public void createProgrammingExercise_checkoutSolutionRepositoryProgrammingLanguageNotSupported_badRequest(ProgrammingLanguage programmingLanguage) throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise.setCheckoutSolutionRepository(true);
        request.post(ROOT + SETUP, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_sourceExerciseIdNegative_badRequest() throws Exception {
        programmingExercise.setId(-1L);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void importProgrammingExerciseMaxScoreNullBadRequest() throws Exception {
        programmingExercise.setMaxScore(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise.setAllowOnlineEditor(false);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_noProgrammingLanguage_badRequest() throws Exception {
        programmingExercise.setProgrammingLanguage(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), programmingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_templateIdDoesNotExist_notFound() throws Exception {
        programmingExercise.setShortName("newShortName");
        programmingExercise.setTitle("newTitle");
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle(programmingExercise.getTitle() + "change");
        programmingExerciseInExam.setId(null);
        programmingExerciseInExam.setTitle(programmingExerciseInExam.getTitle() + "change");
        // short name will still be the same
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_sameTitleInCourse_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName(programmingExercise.getShortName() + "change");
        programmingExerciseInExam.setId(null);
        programmingExerciseInExam.setShortName(programmingExerciseInExam.getShortName() + "change");
        // title will still be the same
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        var id = programmingExercise.getId();
        programmingExercise.setId(null);
        programmingExercise.setStaticCodeAnalysisEnabled(null);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", String.valueOf(id)), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @CsvSource({ "false, false", "true, false", "false, true", })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_scaChanged_badRequest(boolean recreateBuildPlan, boolean updateTemplate) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(recreateBuildPlan));
        params.add("updateTemplate", String.valueOf(updateTemplate));

        // false -> true
        var sourceId = programmingExercise.getId();
        programmingExercise.setId(null);
        programmingExercise.setTitle("NewTitle1");
        programmingExercise.setShortName("NewShortname1");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", String.valueOf(sourceId)), programmingExercise, ProgrammingExercise.class, params,
                HttpStatus.BAD_REQUEST);

        // true -> false
        var programmingExerciseSca = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        sourceId = programmingExerciseSca.getId();
        programmingExerciseSca.setId(null);
        programmingExerciseSca.setStaticCodeAnalysisEnabled(false);
        programmingExerciseSca.setMaxStaticCodeAnalysisPenalty(null);
        programmingExerciseSca.setTitle("NewTitle2");
        programmingExerciseSca.setShortName("NewShortname2");
        request.postWithResponseBody(ROOT + IMPORT.replace("{sourceExerciseId}", String.valueOf(sourceId)), programmingExerciseSca, ProgrammingExercise.class, params,
                HttpStatus.BAD_REQUEST);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockProjectKeyExists(programmingExercise);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_bambooProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockCheckIfProjectExists(programmingExercise, false);
        bambooRequestMockProvider.mockProjectKeyExists(programmingExercise);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockCheckIfProjectExists(programmingExercise, true);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_bambooProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockCheckIfProjectExists(programmingExercise, false);
        bambooRequestMockProvider.mockCheckIfProjectExists(programmingExercise, true);
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", "1337"), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void exportSubmissionsByStudentLogins_notInstructorForExercise_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.post(getDefaultAPIEndpointForExportRepos(), getOptions(), HttpStatus.FORBIDDEN);
    }

    @NotNull
    private String getDefaultAPIEndpointForExportRepos() {
        return ROOT + EXPORT_SUBMISSIONS_BY_PARTICIPANTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())).replace("{participantIdentifiers}", "1,2,3");
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
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId() + 1337)), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    public void generateStructureOracleForExercise_userIsNotAdminInCourse_badRequest() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())), programmingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void generateStructureOracleForExercise_invalidPackageName_badRequest() throws Exception {
        programmingExercise.setPackageName(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())), programmingExercise, HttpStatus.BAD_REQUEST);

        programmingExercise.setPackageName("ab");
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put(ROOT + GENERATE_TESTS.replace("{exerciseId}", String.valueOf(programmingExercise.getId())), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void hasAtLeastOneStudentResult_exerciseDoesNotExist_notFound() throws Exception {
        request.get(ROOT + TEST_CASE_STATE.replace("{exerciseId}", String.valueOf(programmingExercise.getId() + 1337)), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "tutoralt1", roles = "TA")
    public void hasAtLeastOneStudentResult_isNotTeachingAssistant_forbidden() throws Exception {
        database.addTeachingAssistant("other-tutors", "tutoralt");
        request.get(ROOT + TEST_CASE_STATE.replace("{exerciseId}", String.valueOf(programmingExercise.getId())), HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getTestCases_asTutor() throws Exception {
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        final List<ProgrammingExerciseTestCase> returnedTests = request.getList(ROOT + endpoint, HttpStatus.OK, ProgrammingExerciseTestCase.class);
        final List<ProgrammingExerciseTestCase> testsInDB = new ArrayList<>(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        returnedTests.forEach(testCase -> testCase.setExercise(programmingExercise));
        assertThat(returnedTests).containsExactlyInAnyOrderElementsOf(testsInDB);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void getTestCases_asStudent_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.getList(ROOT + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    @Test
    @WithMockUser(username = "other-teaching-assistant1", roles = "TA")
    public void getTestCases_tutorInOtherCourse_forbidden() throws Exception {
        database.addTeachingAssistant("other-teaching-assistants", "other-teaching-assistant");
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        request.getList(ROOT + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateTestCases_asInstrutor() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> {
            final var testCaseUpdate = new ProgrammingExerciseTestCaseDTO();
            testCaseUpdate.setId(testCase.getId());
            testCaseUpdate.setAfterDueDate(true);
            testCaseUpdate.setWeight(testCase.getId() + 42.0);
            testCaseUpdate.setBonusMultiplier(testCase.getId() + 1.0);
            testCaseUpdate.setBonusPoints(testCase.getId() + 2.0);
            return testCaseUpdate;
        }).collect(Collectors.toList());
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        final var testCasesResponse = request.patchWithResponseBody(ROOT + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testCasesInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(new HashSet<>(testCasesResponse)).usingElementComparatorIgnoringFields("exercise").containsExactlyInAnyOrderElementsOf(testCasesInDB);
        assertThat(testCasesResponse).allSatisfy(testCase -> {
            assertThat(testCase.isAfterDueDate()).isTrue();
            assertThat(testCase.getWeight()).isEqualTo(testCase.getId() + 42);
            assertThat(testCase.getBonusMultiplier()).isEqualTo(testCase.getId() + 1.0);
            assertThat(testCase.getBonusPoints()).isEqualTo(testCase.getId() + 2.0);
        });
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateTestCases_nonExistingExercise_notFound() throws Exception {
        final var update = new ProgrammingExerciseTestCaseDTO();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId() + 1337));
        request.patchWithResponseBody(ROOT + endpoint, List.of(update), String.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    public void updateTestCases_instructorInWrongCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "other-instructor");
        final var update = new ProgrammingExerciseTestCaseDTO();
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        request.patchWithResponseBody(ROOT + endpoint, List.of(update), String.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateTestCases_testCaseWeightSmallerThanZero_badRequest() throws Exception {
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> {
            final var testCaseUpdate = new ProgrammingExerciseTestCaseDTO();
            testCaseUpdate.setId(testCase.getId());
            testCaseUpdate.setAfterDueDate(true);
            testCaseUpdate.setWeight(0D);
            testCaseUpdate.setBonusMultiplier(testCase.getId() + 1.0);
            testCaseUpdate.setBonusPoints(testCase.getId() + 2.0);
            return testCaseUpdate;
        }).collect(Collectors.toList());
        final var endpoint = ProgrammingExerciseTestCaseResource.Endpoints.UPDATE_TEST_CASES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));

        request.patchWithResponseBody(ROOT + endpoint, updates, String.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resetTestCaseWeights_asInstructor() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        final var endpoint = ProgrammingExerciseGradingResource.RESET.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).forEach(test -> {
            test.setWeight(42.0);
            programmingExerciseTestCaseRepository.saveAndFlush(test);
        });

        final var testCasesResponse = request.patchWithResponseBody(ROOT + endpoint, "{}", new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        // Otherwise the HashSet for comparison can't be created because exercise id is used for the hashCode
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testsInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(testCasesResponse).containsExactlyInAnyOrderElementsOf(testsInDB);
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getWeight()).isEqualTo(1));
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getBonusMultiplier()).isEqualTo(1.0));
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getBonusPoints()).isEqualTo(0.0));
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    public void resetTestCaseWeights_instructorInWrongCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "other-instructor");
        final var endpoint = ProgrammingExerciseGradingResource.RESET.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.patchWithResponseBody(ROOT + endpoint, "{}", String.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void lockAllRepositories_asStudent_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseResource.Endpoints.LOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void lockAllRepositories_asTutor_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseResource.Endpoints.LOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void lockAllRepositories() throws Exception {
        bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(BitbucketRequestMockProvider.repositorySlugOf(participation1), programmingExercise.getProjectKey(),
                participation1.getStudents());
        bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(BitbucketRequestMockProvider.repositorySlugOf(participation2), programmingExercise.getProjectKey(),
                participation2.getStudents());

        final var endpoint = ProgrammingExerciseResource.Endpoints.LOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.OK);

        verify(versionControlService, times(1)).setRepositoryPermissionsToReadOnly(participation1.getVcsRepositoryUrl(), programmingExercise.getProjectKey(),
                participation1.getStudents());
        verify(versionControlService, times(1)).setRepositoryPermissionsToReadOnly(participation2.getVcsRepositoryUrl(), programmingExercise.getProjectKey(),
                participation2.getStudents());

        database.changeUser("instructor1");

        var notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Intructor get notified that lock operations were successful")
                .anyMatch(n -> n.getText().contains(Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_LOCK_OPERATION_NOTIFICATION))
                .noneMatch(n -> n.getText().contains(Constants.PROGRAMMING_EXERCISE_FAILED_LOCK_OPERATIONS_NOTIFICATION));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void unlockAllRepositories_asStudent_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseResource.Endpoints.UNLOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void unlockAllRepositories_asTutor_forbidden() throws Exception {
        final var endpoint = ProgrammingExerciseResource.Endpoints.UNLOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void unlockAllRepositories() throws Exception {
        bitbucketRequestMockProvider.mockConfigureRepository(programmingExercise, participation1.getParticipantIdentifier(), participation1.getStudents(), false);
        bitbucketRequestMockProvider.mockConfigureRepository(programmingExercise, participation2.getParticipantIdentifier(), participation2.getStudents(), false);

        final var endpoint = ProgrammingExerciseResource.Endpoints.UNLOCK_ALL_REPOSITORIES.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(ROOT + endpoint, null, HttpStatus.OK);

        verify(versionControlService, times(1)).configureRepository(programmingExercise, participation1.getVcsRepositoryUrl(), participation1.getStudents(), true);
        verify(versionControlService, times(1)).configureRepository(programmingExercise, participation2.getVcsRepositoryUrl(), participation2.getStudents(), true);

        database.changeUser("instructor1");

        var notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Intructor get notified that unlock operations were successful")
                .anyMatch(n -> n.getText().contains(Constants.PROGRAMMING_EXERCISE_SUCCESSFUL_UNLOCK_OPERATION_NOTIFICATION))
                .noneMatch(n -> n.getText().contains(Constants.PROGRAMMING_EXERCISE_FAILED_UNLOCK_OPERATIONS_NOTIFICATION));
    }
}
