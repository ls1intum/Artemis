package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.StreamUtils;

import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.exercise.programmingexercise.ContinuousIntegrationTestService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
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

        var exerciseRepoUrl = programmingExercise.getVcsTemplateRepositoryUrl();
        var testsRepoUrl = programmingExercise.getVcsTestRepositoryUrl();
        var solutionRepoUrl = programmingExercise.getVcsSolutionRepositoryUrl();

        MockedStatic<StreamUtils> mockedStreamUtils = mockStatic(StreamUtils.class);
        mockedStreamUtils.when(() -> StreamUtils.copyToString(any(InputStream.class), any())).thenThrow(IOException.class);

        assertThatIllegalStateException()
                .isThrownBy(() -> continuousIntegrationService.createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUrl, testsRepoUrl, solutionRepoUrl))
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

        var exerciseRepoUrl = programmingExercise.getVcsTemplateRepositoryUrl();
        var testsRepoUrl = programmingExercise.getVcsTestRepositoryUrl();
        var solutionRepoUrl = programmingExercise.getVcsSolutionRepositoryUrl();

        var finalProgrammingExercise = programmingExercise;
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(
                        () -> continuousIntegrationService.createBuildPlanForExercise(finalProgrammingExercise, TEMPLATE.getName(), exerciseRepoUrl, testsRepoUrl, solutionRepoUrl))
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

        verify(jenkinsServer, times(1)).createFolder(eq(null), eq(programmingExercise.getProjectKey()), any());
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
            String templateRepoUrl = programmingExercise.getTemplateRepositoryUrl();
            continuousIntegrationService.updatePlanRepository(projectKey, planName, ASSIGNMENT_REPO_NAME, null, participation.getRepositoryUrl(), templateRepoUrl, "main",
                    List.of());
        }).withMessageStartingWith("Error trying to configure build plan in Jenkins");
    }
}
