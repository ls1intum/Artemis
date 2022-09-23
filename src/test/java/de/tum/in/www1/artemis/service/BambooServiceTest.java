package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.programmingexercise.ContinuousIntegrationTestService;

class BambooServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ContinuousIntegrationTestService continuousIntegrationTestService;

    /**
     * This method initializes the test case by setting up a local repo
     */
    @BeforeEach
    void initTestCase() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        continuousIntegrationTestService.setup(this, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws IOException {
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
        continuousIntegrationTestService.tearDown();
    }

    /**
     * This method tests if the local repo is deleted if the exercise cannot be accessed
     */
    @Test
    @WithMockUser(username = "student1")
    void performEmptySetupCommitWithNullExercise() {
        // test performEmptyCommit() with empty exercise
        continuousIntegrationTestService.getParticipation().setProgrammingExercise(null);
        continuousIntegrationService.performEmptySetupCommit(continuousIntegrationTestService.getParticipation());

        Repository repo = gitService.getExistingCheckedOutRepositoryByLocalPath(continuousIntegrationTestService.getLocalRepo().localRepoFile.toPath(), null);
        assertThat(repo).as("local repository has been deleted").isNull();

        Repository originRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(continuousIntegrationTestService.getLocalRepo().originRepoFile.toPath(), null);
        assertThat(originRepo).as("origin repository has not been deleted").isNotNull();
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
    @WithMockUser(username = "student1")
    void testConfigureBuildPlan() throws Exception {
        continuousIntegrationTestService.testConfigureBuildPlan();
    }
}
