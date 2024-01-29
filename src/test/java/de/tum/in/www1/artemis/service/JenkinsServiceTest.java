package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.StreamUtils;

import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.BuildPlan;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.exercise.programmingexercise.ContinuousIntegrationTestService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.jenkins.build_plan.JenkinsBuildPlanUtils;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportService;

class JenkinsServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "jenkinsservicetest";

    @Autowired
    private ContinuousIntegrationTestService continuousIntegrationTestService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseImportService programmingExerciseImportService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    /**
     * This method initializes the test case by setting up a local repo
     */
    @BeforeEach
    void initTestCase() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        continuousIntegrationTestService.setup(TEST_PREFIX, this, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        continuousIntegrationTestService.tearDown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetBuildStatusNotFound() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusNotFound();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetBuildStatusInactive1() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusInactive1();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetBuildStatusInactive2() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusInactive2();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetBuildStatusQueued() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusQueued();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetBuildStatusBuilding() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusBuilding();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetBuildStatusFails() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusFails();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testHealthRunning() throws Exception {
        continuousIntegrationTestService.testHealthRunning();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testHealthNotRunning() throws Exception {
        continuousIntegrationTestService.testHealthNotRunning();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testHealthException() throws Exception {
        continuousIntegrationTestService.testHealthException();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testCreateBuildPlanForExerciseThrowsExceptionOnTemplateError() {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        var exerciseRepoUri = programmingExercise.getVcsTemplateRepositoryUri();
        var testsRepoUri = programmingExercise.getVcsTestRepositoryUri();
        var solutionRepoUri = programmingExercise.getVcsSolutionRepositoryUri();

        MockedStatic<StreamUtils> mockedStreamUtils = mockStatic(StreamUtils.class);
        mockedStreamUtils.when(() -> StreamUtils.copyToString(any(InputStream.class), any())).thenThrow(IOException.class);

        assertThatIllegalStateException()
                .isThrownBy(() -> continuousIntegrationService.createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUri, testsRepoUri, solutionRepoUri))
                .withMessageStartingWith("Error loading template Jenkins build XML: ");

        mockedStreamUtils.close();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "VHDL", "ASSEMBLER", "OCAML" }, mode = EnumSource.Mode.INCLUDE)
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testCreateBuildPlanForExerciseThrowsExceptionOnTemplateError(ProgrammingLanguage programmingLanguage) {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        var exerciseRepoUri = programmingExercise.getVcsTemplateRepositoryUri();
        var testsRepoUri = programmingExercise.getVcsTestRepositoryUri();
        var solutionRepoUri = programmingExercise.getVcsSolutionRepositoryUri();

        var finalProgrammingExercise = programmingExercise;
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(
                        () -> continuousIntegrationService.createBuildPlanForExercise(finalProgrammingExercise, TEMPLATE.getName(), exerciseRepoUri, testsRepoUri, solutionRepoUri))
                .withMessageEndingWith("templates are not available for Jenkins.");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testImportBuildPlansThrowsExceptionOnGivePermissions() throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        jenkinsRequestMockProvider.mockCreateProjectForExercise(programmingExercise, false);
        jenkinsRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockGivePlanPermissionsThrowException(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());

        assertThatExceptionOfType(JenkinsException.class).isThrownBy(() -> programmingExerciseImportService.importBuildPlans(programmingExercise, programmingExercise))
                .withMessageStartingWith("Cannot give assign permissions to plan");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testDeleteBuildPlan() throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        jenkinsRequestMockProvider.mockCreateProjectForExercise(programmingExercise, false);
        jenkinsRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockGivePlanPermissionsThrowException(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());

        assertThatExceptionOfType(JenkinsException.class).isThrownBy(() -> programmingExerciseImportService.importBuildPlans(programmingExercise, programmingExercise))
                .withMessageStartingWith("Cannot give assign permissions to plan");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testRecreateBuildPlanDeletedFolder() throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        final String templateJobName = programmingExercise.getProjectKey() + "-" + TEMPLATE.getName();
        final String solutionJobName = programmingExercise.getProjectKey() + "-" + SOLUTION.getName();

        jenkinsRequestMockProvider.mockCreateProjectForExercise(programmingExercise, false);
        jenkinsRequestMockProvider.mockGetJob(programmingExercise.getProjectKey(), templateJobName, null, false);
        jenkinsRequestMockProvider.mockDeleteBuildPlan(programmingExercise.getProjectKey(), templateJobName, false);
        jenkinsRequestMockProvider.mockDeleteBuildPlan(programmingExercise.getProjectKey(), solutionJobName, false);
        jenkinsRequestMockProvider.mockCreateBuildPlan(programmingExercise.getProjectKey(), TEMPLATE.getName(), false);
        jenkinsRequestMockProvider.mockCreateBuildPlan(programmingExercise.getProjectKey(), SOLUTION.getName(), false);
        when(jenkinsServer.getJob(programmingExercise.getProjectKey())).thenReturn(null).thenReturn(new JobWithDetails());

        continuousIntegrationService.recreateBuildPlansForExercise(programmingExercise);

        verify(jenkinsServer).createFolder(eq(null), eq(programmingExercise.getProjectKey()), any());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testFailToUpdatePlanRepositoryBadRequest() throws Exception {
        testFailToUpdatePlanRepositoryRestClientException(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testFailToUpdatePlanRepositoryInternalError() throws Exception {
        testFailToUpdatePlanRepositoryRestClientException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void testFailToUpdatePlanRepositoryRestClientException(HttpStatus expectedStatus) throws IOException, URISyntaxException {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        String projectKey = programmingExercise.getProjectKey();
        String planName = programmingExercise.getProjectKey();

        jenkinsRequestMockProvider.mockUpdatePlanRepository(projectKey, planName, expectedStatus);

        assertThatExceptionOfType(JenkinsException.class).isThrownBy(() -> {
            String templateRepoUri = programmingExercise.getTemplateRepositoryUri();
            continuousIntegrationService.updatePlanRepository(projectKey, planName, ASSIGNMENT_REPO_NAME, null, participation.getRepositoryUri(), templateRepoUri, "main");
        }).withMessageStartingWith("Error trying to configure build plan in Jenkins");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testUpdateBuildPlanRepoUrisForStudent() throws Exception {
        MockedStatic<JenkinsBuildPlanUtils> mockedUtils = mockStatic(JenkinsBuildPlanUtils.class);
        ArgumentCaptor<String> toBeReplacedCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> replacementCaptor = ArgumentCaptor.forClass(String.class);
        mockedUtils.when(() -> JenkinsBuildPlanUtils.replaceScriptParameters(any(), toBeReplacedCaptor.capture(), replacementCaptor.capture())).thenCallRealMethod();

        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        String projectKey = programmingExercise.getProjectKey();
        String planName = programmingExercise.getProjectKey();

        String templateRepoUri = programmingExercise.getTemplateRepositoryUri();
        jenkinsRequestMockProvider.mockUpdatePlanRepository(projectKey, planName, HttpStatus.OK);

        continuousIntegrationService.updatePlanRepository(projectKey, planName, ASSIGNMENT_REPO_NAME, null, participation.getRepositoryUri(), templateRepoUri, "main");

        assertThat(toBeReplacedCaptor.getValue()).contains("-exercise.git");
        assertThat(replacementCaptor.getValue()).contains(TEST_PREFIX + "student1.git");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testCopyBuildPlan() throws IOException {
        var course = courseUtilService.addEmptyCourse();

        ProgrammingExercise sourceExercise = new ProgrammingExercise();
        course.addExercises(sourceExercise);
        sourceExercise.generateAndSetProjectKey();
        sourceExercise = programmingExerciseRepository.save(sourceExercise);
        String buildPlanContent = "sample text";
        buildPlanRepository.setBuildPlanForExercise(buildPlanContent, sourceExercise);

        ProgrammingExercise targetExercise = new ProgrammingExercise();
        course.addExercises(targetExercise);
        targetExercise.generateAndSetProjectKey();
        targetExercise = programmingExerciseRepository.save(targetExercise);

        jenkinsRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), targetExercise.getProjectKey());

        continuousIntegrationService.copyBuildPlan(sourceExercise, "", targetExercise, "", "", true);
        BuildPlan sourceBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(sourceExercise.getId());
        BuildPlan targetBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(targetExercise.getId());
        assertThat(sourceBuildPlan).isEqualTo(targetBuildPlan);
    }

    /**
     * The old exercise uses the old-style build plans that are stored in Jenkins directly rather than in Artemis.
     */
    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testCopyLegacyBuildPlan() throws IOException {
        var course = courseUtilService.addEmptyCourse();

        ProgrammingExercise sourceExercise = new ProgrammingExercise();
        course.addExercises(sourceExercise);
        sourceExercise = programmingExerciseRepository.save(sourceExercise);

        Optional<BuildPlan> sourceBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(sourceExercise.getId());
        assertThat(sourceBuildPlan).isEmpty();

        ProgrammingExercise targetExercise = new ProgrammingExercise();
        course.addExercises(targetExercise);
        targetExercise.generateAndSetProjectKey();
        targetExercise = programmingExerciseRepository.save(targetExercise);

        jenkinsRequestMockProvider.mockCopyBuildPlan(sourceExercise.getProjectKey(), targetExercise.getProjectKey());

        continuousIntegrationService.copyBuildPlan(sourceExercise, "", targetExercise, "", "", true);

        Optional<BuildPlan> targetBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(targetExercise.getId());
        assertThat(targetBuildPlan).isEmpty();
    }
}
