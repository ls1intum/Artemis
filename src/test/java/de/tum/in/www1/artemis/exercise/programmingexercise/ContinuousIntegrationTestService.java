package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

@Service
public class ContinuousIntegrationTestService {

    @Value("${artemis.continuous-integration.url}")
    private URL ciServerUrl;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private final LocalRepository localRepo = new LocalRepository(defaultBranch);

    private ProgrammingExerciseStudentParticipation participation;

    @Autowired
    private GitService gitService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private MockDelegate mockDelegate;

    private ContinuousIntegrationService continuousIntegrationService;

    public ProgrammingExercise programmingExercise;

    /**
     * This method initializes the test case by setting up a local repo
     */
    public void setup(String testPrefix, MockDelegate mockDelegate, ContinuousIntegrationService continuousIntegrationService) throws Exception {
        this.mockDelegate = mockDelegate;
        this.continuousIntegrationService = continuousIntegrationService;

        userUtilService.addUsers(testPrefix, 2, 0, 0, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();

        // init local repo
        String currentLocalFileName = "currentFileName";
        String currentLocalFileContent = "testContent";
        String currentLocalFolderName = "currentFolderName";
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");
        // add file to the repository folder
        Path filePath = Path.of(localRepo.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent, Charset.defaultCharset());
        // add folder to the repository folder
        filePath = Path.of(localRepo.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(filePath);

        GitUtilService.MockFileRepositoryUri localRepoUri = new GitUtilService.MockFileRepositoryUri(localRepo.localRepoFile);
        // create a participation
        participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, testPrefix + "student1", localRepoUri.getURI());
        assertThat(programmingExercise).as("Exercise was correctly set").isEqualTo(participation.getProgrammingExercise());

        // mock return of git path
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUri(), true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUri(), false);
    }

    public void tearDown() throws IOException {
        reset(gitService);
        localRepo.resetLocalRepo();
    }

    public ProgrammingExerciseStudentParticipation getParticipation() {
        return participation;
    }

    public LocalRepository getLocalRepo() {
        return localRepo;
    }

    public void testGetBuildStatusNotFound() throws Exception {
        mockDelegate.mockGetBuildPlan(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), false, false, false, false);

        // INACTIVE // same as not found
        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusInactive1() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, false, false, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusInactive2() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, false, true, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusQueued() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, false, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is queued").isEqualTo(ContinuousIntegrationService.BuildStatus.QUEUED);
    }

    public void testGetBuildStatusBuilding() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, true, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is building").isEqualTo(ContinuousIntegrationService.BuildStatus.BUILDING);
    }

    public void testGetBuildStatusFails() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, true, true);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testHealthRunning() throws Exception {
        mockDelegate.mockHealthInCiService(true, HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isTrue();
    }

    public void testHealthNotRunning() throws Exception {
        mockDelegate.mockHealthInCiService(false, HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isFalse();
    }

    public void testHealthException() throws Exception {
        mockDelegate.mockHealthInCiService(false, HttpStatus.INTERNAL_SERVER_ERROR);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo()).containsEntry("url", ciServerUrl);
        assertThat(health.isUp()).isFalse();
        assertThat(health.getException()).isNotNull();
    }

    public void testConfigureBuildPlan() throws Exception {
        mockDelegate.mockConfigureBuildPlan(participation, defaultBranch);
        continuousIntegrationService.configureBuildPlan(participation, defaultBranch);
        mockDelegate.verifyMocks();
    }
}
