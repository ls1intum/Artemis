package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

@Service
public class ContinuousIntegrationTestService {

    @Value("${artemis.continuous-integration.url}")
    private URL ciServerUrl;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    LocalRepository localRepo = new LocalRepository();

    GitUtilService.MockFileRepositoryUrl localRepoUrl;

    ProgrammingExerciseStudentParticipation participation;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    GitService gitService;

    MockDelegate mockDelegate;

    ContinuousIntegrationService continuousIntegrationService;

    /**
     * This method initializes the test case by setting up a local repo
     */
    public void setup(MockDelegate mockDelegate, ContinuousIntegrationService continuousIntegrationService) throws Exception {
        this.mockDelegate = mockDelegate;
        this.continuousIntegrationService = continuousIntegrationService;

        database.addUsers(2, 0, 0);
        database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findAll().get(0);

        // init local repo
        String currentLocalFileName = "currentFileName";
        String currentLocalFileContent = "testContent";
        String currentLocalFolderName = "currentFolderName";
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");
        // add file to the repository folder
        Path filePath = Paths.get(localRepo.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent, Charset.defaultCharset());
        // add folder to the repository folder
        filePath = Paths.get(localRepo.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(filePath).toFile();

        localRepoUrl = new GitUtilService.MockFileRepositoryUrl(localRepo.localRepoFile);
        // create a participation
        participation = database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, "student1", localRepoUrl.getURL());
        assertThat(programmingExercise).as("Exercise was correctly set").isEqualTo(participation.getProgrammingExercise());

        // mock return of git path
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUrl(), true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUrl(), false);
    }

    public void tearDown() throws IOException {
        database.resetDatabase();
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
        mockDelegate.mockGetBuildPlan(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), false, false, false);

        // INACTIVE // same as not found
        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusInactive1() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, false, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusInactive2() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, false, true);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(ContinuousIntegrationService.BuildStatus.INACTIVE);
    }

    public void testGetBuildStatusQueued() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, false);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is queued").isEqualTo(ContinuousIntegrationService.BuildStatus.QUEUED);
    }

    public void testGetBuildStatusBuilding() throws Exception {
        var projectKey = participation.getProgrammingExercise().getProjectKey();
        var buildPlanId = participation.getBuildPlanId();
        mockDelegate.mockGetBuildPlan(projectKey, buildPlanId, true, true, true);

        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is building").isEqualTo(ContinuousIntegrationService.BuildStatus.BUILDING);
    }

    public void testHealthRunning() throws Exception {
        mockDelegate.mockHealthInCiService(true, HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(ciServerUrl);
        assertThat(health.isUp()).isEqualTo(true);
    }

    public void testHealthNotRunning() throws Exception {
        mockDelegate.mockHealthInCiService(false, HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(ciServerUrl);
        assertThat(health.isUp()).isEqualTo(false);
    }

    public void testHealthException() throws Exception {
        mockDelegate.mockHealthInCiService(false, HttpStatus.INTERNAL_SERVER_ERROR);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(ciServerUrl);
        assertThat(health.isUp()).isEqualTo(false);
        assertThat(health.getException()).isNotNull();
    }
}
