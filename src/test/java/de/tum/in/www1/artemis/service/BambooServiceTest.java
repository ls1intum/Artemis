package de.tum.in.www1.artemis.service;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.exercise.programmingexercise.ContinuousIntegrationTestService;

class BambooServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "bambooservicetest";

    @Autowired
    private ContinuousIntegrationTestService continuousIntegrationTestService;

    /**
     * This method initializes the test case by setting up a local repo
     */
    @BeforeEach
    void initTestCase() throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        continuousIntegrationTestService.setup(TEST_PREFIX, this, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws IOException {
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
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
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testConfigureBuildPlan() throws Exception {
        continuousIntegrationTestService.testConfigureBuildPlan();
    }
}
