package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService.BuildStatus;
import de.tum.in.www1.artemis.util.*;

public class BambooServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    DatabaseUtilService database;

    private ProgrammingExercise programmingExercise;

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
        programmingExercise = programmingExerciseRepository.findAll().get(0);

        // init local repo
        String currentLocalFileName = "currentFileName";
        String currentLocalFileContent = "testContent";
        String currentLocalFolderName = "currentFolderName";
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");
        // add file to the repository folder
        Path filePath = Paths.get(localRepo.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent);
        // add folder to the repository folder
        filePath = Paths.get(localRepo.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(filePath).toFile();

        localRepoUrl = new GitUtilService.MockFileRepositoryUrl(localRepo.localRepoFile);
        // create a participation
        participation = database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, "student1", localRepoUrl.getURL());
        assertThat(programmingExercise).as("Exercise was correctly set").isEqualTo(participation.getProgrammingExercise());

        // mock return of git path
        doReturn(gitService.getRepositoryByLocalPath(localRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(participation.getRepositoryUrlAsUrl(), true);
        doReturn(gitService.getRepositoryByLocalPath(localRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(participation.getRepositoryUrlAsUrl(), false);
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        localRepo.resetLocalRepo();
    }

    /**
     * This method tests if the local repo is deleted if the exercise cannot be accessed
     */
    @Test
    @WithMockUser(username = "student1")
    public void performEmptySetupCommit_withNullExercise() {
        // test performEmptyCommit() with empty exercise
        participation.setProgrammingExercise(null);
        continuousIntegrationService.performEmptySetupCommit(participation);

        Repository repo = gitService.getRepositoryByLocalPath(localRepo.localRepoFile.toPath());
        assertThat(repo).as("local repository has been deleted").isNull();

        Repository originRepo = gitService.getRepositoryByLocalPath(localRepo.originRepoFile.toPath());
        assertThat(originRepo).as("origin repository has not been deleted").isNotNull();
    }

    /**
     * This method tests if the a build status is correctly
     */
    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatus() {
        Map<String, Boolean> result;
        BuildStatus buildStatus;

        // INACTIVE
        doReturn(null).when(continuousIntegrationService).retrieveBuildStatus(any());
        buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(BuildStatus.INACTIVE);

        result = new HashMap<>(Map.of("isActive", false, "isBuilding", false));
        doReturn(result).when(continuousIntegrationService).retrieveBuildStatus(any());
        buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(BuildStatus.INACTIVE);

        result = new HashMap<>(Map.of("isActive", false, "isBuilding", true));
        doReturn(result).when(continuousIntegrationService).retrieveBuildStatus(any());
        buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is inactive").isEqualTo(BuildStatus.INACTIVE);

        // QUEUED
        result = new HashMap<>(Map.of("isActive", true, "isBuilding", false));
        doReturn(result).when(continuousIntegrationService).retrieveBuildStatus(any());
        buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is queued").isEqualTo(BuildStatus.QUEUED);

        // BUILDING
        result = new HashMap<>(Map.of("isActive", true, "isBuilding", true));
        doReturn(result).when(continuousIntegrationService).retrieveBuildStatus(any());
        buildStatus = continuousIntegrationService.getBuildStatus(participation);
        assertThat(buildStatus).as("buildStatus is building").isEqualTo(BuildStatus.BUILDING);
    }
}
