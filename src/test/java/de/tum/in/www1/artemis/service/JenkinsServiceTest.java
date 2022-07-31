package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.StreamUtils;

import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.ContinuousIntegrationTestService;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportService;

class JenkinsServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ContinuousIntegrationTestService continuousIntegrationTestService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ProgrammingExerciseImportService programmingExerciseImportService;

    /**
     * This method initializes the test case by setting up a local repo
     */
    @BeforeEach
    void initTestCase() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        continuousIntegrationTestService.setup(this, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws IOException {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        continuousIntegrationTestService.tearDown();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetBuildStatusNotFound() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusNotFound();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetBuildStatusInactive1() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusInactive1();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetBuildStatusInactive2() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusInactive2();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetBuildStatusQueued() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusQueued();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetBuildStatusBuilding() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusBuilding();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetBuildStatusFails() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusFails();
    }

    @Test
    @WithMockUser(username = "student1")
    void testHealthRunning() throws Exception {
        continuousIntegrationTestService.testHealthRunning();
    }

    @Test
    @WithMockUser(username = "student1")
    void testHealthNotRunning() throws Exception {
        continuousIntegrationTestService.testHealthNotRunning();
    }

    @Test
    @WithMockUser(username = "student1")
    void testHealthException() throws Exception {
        continuousIntegrationTestService.testHealthException();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testCreateBuildPlanForExerciseThrowsExceptionOnTemplateError() throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTestCasesToProgrammingExercise(programmingExercise);

        var exerciseRepoUrl = programmingExercise.getVcsTemplateRepositoryUrl();
        var testsRepoUrl = programmingExercise.getVcsTestRepositoryUrl();
        var solutionRepoUrl = programmingExercise.getVcsSolutionRepositoryUrl();

        MockedStatic<StreamUtils> mockedStreamUtils = mockStatic(StreamUtils.class);
        mockedStreamUtils.when(() -> StreamUtils.copyToString(any(InputStream.class), any())).thenThrow(IOException.class);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            continuousIntegrationService.createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUrl, testsRepoUrl, solutionRepoUrl);
        });

        mockedStreamUtils.close();
        assertThat(exception.getMessage()).startsWith("Error loading template Jenkins build XML: ");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "VHDL", "ASSEMBLER", "OCAML" }, mode = EnumSource.Mode.INCLUDE)
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testCreateBuildPlanForExerciseThrowsExceptionOnTemplateError(ProgrammingLanguage programmingLanguage) throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTestCasesToProgrammingExercise(programmingExercise);

        var exerciseRepoUrl = programmingExercise.getVcsTemplateRepositoryUrl();
        var testsRepoUrl = programmingExercise.getVcsTestRepositoryUrl();
        var solutionRepoUrl = programmingExercise.getVcsSolutionRepositoryUrl();

        var finalProgrammingExercise = programmingExercise;
        Exception exception = assertThrows(UnsupportedOperationException.class, () -> {
            continuousIntegrationService.createBuildPlanForExercise(finalProgrammingExercise, TEMPLATE.getName(), exerciseRepoUrl, testsRepoUrl, solutionRepoUrl);
        });

        assertThat(exception.getMessage()).endsWith("templates are not available for Jenkins.");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testImportBuildPlansThrowsExceptionOnGivePermissions() throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTestCasesToProgrammingExercise(programmingExercise);

        jenkinsRequestMockProvider.mockCreateProjectForExercise(programmingExercise, false);
        jenkinsRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockGivePlanPermissionsThrowException(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());

        Exception exception = assertThrows(JenkinsException.class, () -> {
            programmingExerciseImportService.importBuildPlans(programmingExercise, programmingExercise);
        });
        assertThat(exception.getMessage()).startsWith("Cannot give assign permissions to plan");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testDeleteBuildPlan() throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTestCasesToProgrammingExercise(programmingExercise);

        jenkinsRequestMockProvider.mockCreateProjectForExercise(programmingExercise, false);
        jenkinsRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());
        jenkinsRequestMockProvider.mockGivePlanPermissionsThrowException(programmingExercise.getProjectKey(), programmingExercise.getProjectKey());

        Exception exception = assertThrows(JenkinsException.class, () -> {
            programmingExerciseImportService.importBuildPlans(programmingExercise, programmingExercise);
        });
        assertThat(exception.getMessage()).startsWith("Cannot give assign permissions to plan");
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testRecreateBuildPlanDeletedFolder() throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTestCasesToProgrammingExercise(programmingExercise);

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
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testFailToUpdatePlanRepositoryBadRequest() throws Exception {
        testFailToUpdatePlanRepositoryRestClientException(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = "instructor1")
    void testFailToUpdatePlanRepositoryInternalError() throws Exception {
        testFailToUpdatePlanRepositoryRestClientException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void testFailToUpdatePlanRepositoryRestClientException(HttpStatus expectedStatus) throws IOException, URISyntaxException {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTestCasesToProgrammingExercise(programmingExercise);
        var participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");

        String projectKey = programmingExercise.getProjectKey();
        String planName = programmingExercise.getProjectKey();

        jenkinsRequestMockProvider.mockUpdatePlanRepository(projectKey, planName, expectedStatus);

        Exception exception = assertThrows(JenkinsException.class, () -> {
            String templateRepoUrl = programmingExercise.getTemplateRepositoryUrl();
            continuousIntegrationService.updatePlanRepository(projectKey, planName, ASSIGNMENT_REPO_NAME, null, participation.getRepositoryUrl(), templateRepoUrl, "main",
                    Optional.empty());
        });
        assertThat(exception.getMessage()).startsWith("Error trying to configure build plan in Jenkins");
    }
}
