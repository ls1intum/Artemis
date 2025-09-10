package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.StreamUtils;

import de.tum.cit.aet.artemis.core.exception.JenkinsException;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlan;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.service.jenkins.build_plan.JenkinsBuildPlanUtils;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobService;

class JenkinsServiceTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "jenkinsservicetest";

    /**
     * This method initializes the test case by setting up a local repo
     */
    @BeforeEach
    void initTestCase() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();
        continuousIntegrationTestService.setup(TEST_PREFIX, this, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws Exception {
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
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
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

        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
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
    void testDeleteBuildPlan() {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        final String projectKey = programmingExercise.getProjectKey();
        final String solutionJobName = projectKey + "-" + SOLUTION.getName();

        jenkinsRequestMockProvider.mockDeleteBuildPlanPlain(projectKey, solutionJobName);

        continuousIntegrationService.deleteBuildPlan(projectKey, solutionJobName);

        jenkinsRequestMockProvider.verifyMocks();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testRecreateBuildPlanDeletedFolder() throws Exception {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        final String projectKey = programmingExercise.getProjectKey();
        final String templateJobName = projectKey + "-" + TEMPLATE.getName();
        final String solutionJobName = projectKey + "-" + SOLUTION.getName();
        final JenkinsJobService.JobWithDetails dummyJob = new JenkinsJobService.JobWithDetails("name", "desc", false);
        final JenkinsJobService.FolderJob dummyFolder = new JenkinsJobService.FolderJob("name", "desc", "");

        jenkinsRequestMockProvider.mockGetFolderJob(projectKey, dummyFolder);
        jenkinsRequestMockProvider.mockGetFolderConfigPlain(projectKey);
        jenkinsRequestMockProvider.mockDeleteBuildPlanPlain(projectKey, solutionJobName);
        jenkinsRequestMockProvider.mockDeleteBuildPlanPlain(projectKey, templateJobName);
        jenkinsRequestMockProvider.mockGetFolderJob(projectKey, dummyFolder);
        jenkinsRequestMockProvider.mockGetJobPlain(projectKey, templateJobName, dummyJob);
        jenkinsRequestMockProvider.mockTriggerBuildPlain(projectKey, templateJobName);
        jenkinsRequestMockProvider.mockGetFolderJob(projectKey, dummyFolder);
        jenkinsRequestMockProvider.mockGetJobPlain(projectKey, solutionJobName, dummyJob);
        jenkinsRequestMockProvider.mockTriggerBuildPlain(projectKey, solutionJobName);

        continuousIntegrationService.recreateBuildPlansForExercise(programmingExercise);

        jenkinsRequestMockProvider.verifyMocks();
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

    private void testFailToUpdatePlanRepositoryRestClientException(HttpStatus expectedStatus) throws IOException {
        var programmingExercise = continuousIntegrationTestService.programmingExercise;
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
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
        try (MockedStatic<JenkinsBuildPlanUtils> mockedUtils = mockStatic(JenkinsBuildPlanUtils.class)) {
            ArgumentCaptor<String> toBeReplacedCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> replacementCaptor = ArgumentCaptor.forClass(String.class);
            mockedUtils.when(() -> JenkinsBuildPlanUtils.replaceScriptParameters(any(), toBeReplacedCaptor.capture(), replacementCaptor.capture())).thenCallRealMethod();

            var programmingExercise = continuousIntegrationTestService.programmingExercise;
            programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
            programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
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
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR", username = TEST_PREFIX + "instructor1")
    void testCopyBuildPlan() throws IOException {
        var course = courseUtilService.addEmptyCourse();

        ProgrammingExercise sourceExercise = new ProgrammingExercise();
        course.addExercises(sourceExercise);
        sourceExercise.generateAndSetProjectKey();
        var buildConfig = new ProgrammingExerciseBuildConfig();
        sourceExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(buildConfig));
        sourceExercise = programmingExerciseRepository.save(sourceExercise);
        String buildPlanContent = "sample text";
        buildPlanRepository.setBuildPlanForExercise(buildPlanContent, sourceExercise);

        ProgrammingExercise targetExercise = new ProgrammingExercise();
        course.addExercises(targetExercise);
        targetExercise.generateAndSetProjectKey();
        var buildConfigTarget = new ProgrammingExerciseBuildConfig();
        targetExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(buildConfigTarget));
        targetExercise = programmingExerciseRepository.save(targetExercise);

        jenkinsRequestMockProvider.mockCopyBuildPlanFromTemplate(sourceExercise.getProjectKey(), targetExercise.getProjectKey(), BuildPlanType.TEMPLATE.getName());

        continuousIntegrationService.copyBuildPlan(sourceExercise, BuildPlanType.TEMPLATE.getName(), targetExercise, "", BuildPlanType.TEMPLATE.getName(), true);
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
        sourceExercise.setShortName("source");
        sourceExercise.generateAndSetProjectKey();
        var buildConfig = new ProgrammingExerciseBuildConfig();
        sourceExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(buildConfig));
        sourceExercise = programmingExerciseRepository.save(sourceExercise);

        Optional<BuildPlan> sourceBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(sourceExercise.getId());
        assertThat(sourceBuildPlan).isEmpty();

        ProgrammingExercise targetExercise = new ProgrammingExercise();
        course.addExercises(targetExercise);
        targetExercise.setShortName("target");
        targetExercise.generateAndSetProjectKey();
        var buildConfigTarget = new ProgrammingExerciseBuildConfig();
        targetExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(buildConfigTarget));
        targetExercise = programmingExerciseRepository.save(targetExercise);
        String targetPlanName = targetExercise.getProjectKey() + "-" + TEMPLATE.getName();
        jenkinsRequestMockProvider.mockCopyBuildPlanFromTemplate(sourceExercise.getProjectKey(), targetExercise.getProjectKey(), targetPlanName);

        continuousIntegrationService.copyBuildPlan(sourceExercise, TEMPLATE.getName(), targetExercise, targetExercise.getProjectName(), TEMPLATE.getName(), true);

        Optional<BuildPlan> targetBuildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(targetExercise.getId());
        assertThat(targetBuildPlan).isEmpty();
    }
}
