package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService.BuildStatus;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildPlanDTO;
import de.tum.in.www1.artemis.util.*;

public class BambooServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    LocalRepository localRepo = new LocalRepository();

    GitUtilService.MockFileRepositoryUrl localRepoUrl;

    ProgrammingExerciseStudentParticipation participation;

    /**
     * This method initializes the test case by setting up a local repo
     */
    @BeforeEach
    public void initTestCase() throws Exception {
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

        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        localRepo.resetLocalRepo();
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
    }

    /**
     * This method tests if the local repo is deleted if the exercise cannot be accessed
     */
    @Test
    @WithMockUser(username = "student1")
    public void performEmptySetupCommitWithNullExercise() {
        // test performEmptyCommit() with empty exercise
        participation.setProgrammingExercise(null);
        continuousIntegrationService.performEmptySetupCommit(participation);

        Repository repo = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.localRepoFile.toPath(), null);
        assertThat(repo).as("local repository has been deleted").isNull();

        Repository originRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepo.originRepoFile.toPath(), null);
        assertThat(originRepo).as("origin repository has not been deleted").isNotNull();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusNotFound() throws URISyntaxException, JsonProcessingException {
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), null);

        // INACTIVE // same as not found
        BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(BuildStatus.INACTIVE);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusInactive1() throws URISyntaxException, JsonProcessingException {

        var buildPlan = new BambooBuildPlanDTO(false, false);
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), buildPlan);
        BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(BuildStatus.INACTIVE);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusInactive2() throws URISyntaxException, JsonProcessingException {

        var buildPlan = new BambooBuildPlanDTO(false, true);
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), buildPlan);
        BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(BuildStatus.INACTIVE);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusQueued() throws URISyntaxException, JsonProcessingException {

        var buildPlan = new BambooBuildPlanDTO(true, false);
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), buildPlan);
        BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is queued").isEqualTo(BuildStatus.QUEUED);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusBuilding() throws URISyntaxException, JsonProcessingException {

        var buildPlan = new BambooBuildPlanDTO(true, true);
        bambooRequestMockProvider.mockGetBuildPlan(participation.getBuildPlanId(), buildPlan);
        BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is building").isEqualTo(BuildStatus.BUILDING);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthRunning() throws URISyntaxException, JsonProcessingException {

        bambooRequestMockProvider.mockHealth("RUNNING", HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(bambooServerUrl);
        assertThat(health.isUp()).isEqualTo(true);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthNotRunning() throws URISyntaxException, JsonProcessingException {

        bambooRequestMockProvider.mockHealth("PAUSED", HttpStatus.OK);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(bambooServerUrl);
        assertThat(health.isUp()).isEqualTo(false);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthException() throws URISyntaxException, JsonProcessingException {

        bambooRequestMockProvider.mockHealth("PAUSED", HttpStatus.INTERNAL_SERVER_ERROR);
        var health = continuousIntegrationService.health();
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(bambooServerUrl);
        assertThat(health.isUp()).isEqualTo(false);
        assertThat(health.getException()).isNotNull();
    }
}
