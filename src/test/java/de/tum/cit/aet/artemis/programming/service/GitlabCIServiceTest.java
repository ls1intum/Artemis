package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.PipelineStatus;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Trigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.GitLabCIException;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.BuildLogStatisticsEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.gitlabci.GitLabCIResultService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationGitlabCIGitlabSamlTest;

class GitlabCIServiceTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    private static final String TEST_PREFIX = "gitlabciservicetest";

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    @Autowired
    private GitLabCIResultService gitLabCIResultService;

    @Autowired
    private BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Long programmingExerciseId;

    @BeforeEach
    void initTestCase() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseId = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId();
    }

    @AfterEach
    void tearDown() throws Exception {
        super.resetMockProvider();
        super.resetSpyBeans();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testHealth() {
        var health = continuousIntegrationService.health();
        assertThat(health.isUp()).isTrue();
        assertThat(health.additionalInfo()).containsEntry("cf.", "Version Control Server").containsEntry("url", gitlabServerUrl);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildStatusQueued() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        programmingExerciseUtilService.createProgrammingSubmission(participation, false, "hash");
        mockGetBuildStatus(PipelineStatus.CREATED);

        var result = getBuildStatusForParticipation(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.QUEUED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildStatusBuilding() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        programmingExerciseUtilService.createProgrammingSubmission(participation, false, "hash");
        mockGetBuildStatus(PipelineStatus.RUNNING);

        var result = getBuildStatusForParticipation(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.BUILDING);
    }

    private ContinuousIntegrationService.BuildStatus getBuildStatusForParticipation(final Participation participation) {
        final var studentParticipation = participationRepository.findByIdWithLatestSubmissionElseThrow(participation.getId());
        assertThat(studentParticipation).isInstanceOf(ProgrammingExerciseParticipation.class);
        return continuousIntegrationService.getBuildStatus((ProgrammingExerciseParticipation) studentParticipation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildStatusInactive() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        programmingExerciseUtilService.createProgrammingSubmission(participation, false, "hash");
        mockGetBuildStatus(PipelineStatus.CANCELED);

        var result = continuousIntegrationService.getBuildStatus(participation);

        assertThat(result).isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExtractAndPersistBuildLogStatistics() {
        final var exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        final var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, false, "hash");

        var buildLogStatisticSizeBefore = buildLogStatisticsEntryRepository.count();
        List<BuildLogEntry> buildLogEntries = new ArrayList<>();
        buildLogEntries.add(new BuildLogEntry(ZonedDateTime.now(), "Scanning for projects...", submission));
        buildLogEntries.add(new BuildLogEntry(ZonedDateTime.now(), "LogEntry", submission));
        buildLogEntries.add(new BuildLogEntry(ZonedDateTime.now(), "Total time:", submission));
        gitLabCIResultService.extractAndPersistBuildLogStatistics(submission, ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, buildLogEntries);
        var buildLogStatisticSizeAfterSuccessfulSave = buildLogStatisticsEntryRepository.count();
        assertThat(buildLogStatisticSizeAfterSuccessfulSave).isEqualTo(buildLogStatisticSizeBefore + 1);
        // TODO: add an assertion on the average data and add more realistic build log entries

        // should not work
        gitLabCIResultService.extractAndPersistBuildLogStatistics(submission, ProgrammingLanguage.JAVA, ProjectType.GRADLE_GRADLE, buildLogEntries);
        gitLabCIResultService.extractAndPersistBuildLogStatistics(submission, ProgrammingLanguage.C, ProjectType.GCC, buildLogEntries);

        var buildLogStatisticSizeAfterUnsuccessfulSave = buildLogStatisticsEntryRepository.count();
        assertThat(buildLogStatisticSizeAfterUnsuccessfulSave).isEqualTo(buildLogStatisticSizeAfterUnsuccessfulSave);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTriggerBuildSuccess() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findWithBuildConfigById(programmingExerciseId).orElseThrow();
        exercise.getBuildConfig().setBranch("main");
        programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
        programmingExerciseRepository.save(exercise);
        final ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockTriggerBuild(null);

        continuousIntegrationTriggerService.triggerBuild(participation);

        verify(gitlab, atLeastOnce()).getPipelineApi();
        verify(gitlab.getPipelineApi(), atLeastOnce()).createPipelineTrigger(any(), anyString());
        verify(gitlab.getPipelineApi()).triggerPipeline(eq(uriService.getRepositoryPathFromRepositoryUri(participation.getVcsRepositoryUri())), any(Trigger.class), anyString(),
                isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTriggerBuildFails() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        final ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockTriggerBuildFailed(null);

        assertThatThrownBy(() -> continuousIntegrationTriggerService.triggerBuild(participation)).isInstanceOf(GitLabCIException.class);

        verify(gitlab, atLeastOnce()).getPipelineApi();
        verify(gitlab.getPipelineApi(), never()).triggerPipeline(eq(uriService.getRepositoryPathFromRepositoryUri(participation.getVcsRepositoryUri())), any(Trigger.class),
                anyString(), isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConfigureBuildPlanSuccess() throws Exception {
        final ProgrammingExercise exercise = programmingExerciseRepository.findWithBuildConfigById(programmingExerciseId).orElseThrow();
        final ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        mockConfigureBuildPlan(participation, defaultBranch);
        continuousIntegrationService.configureBuildPlan(participation, defaultBranch);
        verifyMocks();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConfigureBuildPlanFails() throws GitLabApiException {
        mockAddBuildPlanToGitLabRepositoryConfiguration(true);
        final ProgrammingExercise exercise = programmingExerciseRepository.findWithBuildConfigById(programmingExerciseId).orElseThrow();
        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setRepositoryUri("http://some.test.url/PROJECTNAME/REPONAME-exercise.git");
        assertThatThrownBy(() -> continuousIntegrationService.configureBuildPlan(participation, "main")).isInstanceOf(GitLabCIException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateBuildPlanForExercise() throws GitLabApiException {
        final ProgrammingExercise exercise = programmingExerciseRepository.findWithBuildConfigById(programmingExerciseId).orElseThrow();
        final ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        final String repositoryPath = uriService.getRepositoryPathFromRepositoryUri(participation.getVcsRepositoryUri());
        mockAddBuildPlanToGitLabRepositoryConfiguration(false);

        continuousIntegrationService.createBuildPlanForExercise(exercise, "TEST-EXERCISE", participation.getVcsRepositoryUri(), null, null);

        verify(gitlab, atLeastOnce()).getProjectApi();
        verify(gitlab.getProjectApi(), atLeastOnce()).getProject(eq(repositoryPath));
        verify(gitlab.getProjectApi(), atLeastOnce()).updateProject(any(Project.class));
        verify(gitlab.getProjectApi(), atLeastOnce()).createVariable(anyString(), anyString(), anyString(), any(), anyBoolean(), anyBoolean());
        var buildPlanOptional = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(exercise.getId());
        assertThat(buildPlanOptional).isPresent();
        assertThat(buildPlanOptional.get().getBuildPlan()).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCopyBuildPlan() {
        final Course course = new Course();
        final ProgrammingExercise targetExercise = new ProgrammingExercise();
        course.addExercises(targetExercise);
        targetExercise.generateAndSetProjectKey();

        final String targetProjectKey = targetExercise.getProjectKey();
        final String targetPlanName1 = "TARGETPLANNAME1";
        final String targetPlanName2 = "target-plan-name-#2";

        final String expectedBuildPlanKey1 = targetProjectKey + "-TARGETPLANNAME1";
        final String expectedBuildPlanKey2 = targetProjectKey + "-TARGETPLANNAME2";

        assertThat(continuousIntegrationService.copyBuildPlan(null, null, targetExercise, null, targetPlanName1, false)).isEqualTo(expectedBuildPlanKey1);
        assertThat(continuousIntegrationService.copyBuildPlan(null, null, targetExercise, null, targetPlanName2, false)).isEqualTo(expectedBuildPlanKey2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUnsupportedMethods() {
        continuousIntegrationService.createProjectForExercise(null);
        continuousIntegrationService.removeAllDefaultProjectPermissions(null);
        continuousIntegrationService.givePlanPermissions(null, null);
        continuousIntegrationService.giveProjectPermissions(null, null, null);
        continuousIntegrationService.updatePlanRepository(null, null, null, null, null, null, null);
        continuousIntegrationService.enablePlan(null, null);
        continuousIntegrationService.deleteBuildPlan(null, null);
        continuousIntegrationService.deleteProject(null);
        assertThat(continuousIntegrationService.getWebHookUrl(null, null)).isNotPresent();
        assertThat(continuousIntegrationService.checkIfProjectExists(null, null)).isNull();
        assertThat(continuousIntegrationService.checkIfBuildPlanExists(null, null)).isTrue();
        assertThat(continuousIntegrationService.retrieveLatestArtifact(null)).isNull();
    }
}
