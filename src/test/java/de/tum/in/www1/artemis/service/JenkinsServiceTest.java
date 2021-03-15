package de.tum.in.www1.artemis.service;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.ContinuousIntegrationTestService;

public class JenkinsServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    ContinuousIntegrationTestService continuousIntegrationTestService;

    /**
     * This method initializes the test case by setting up a local repo
     */
    @BeforeEach
    public void initTestCase() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        continuousIntegrationTestService.setup(this, continuousIntegrationService);
    }

    @AfterEach
    public void tearDown() throws IOException {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        continuousIntegrationTestService.tearDown();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusNotFound() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusNotFound();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusInactive1() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusInactive1();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusInactive2() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusInactive2();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusQueued() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusQueued();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetBuildStatusBuilding() throws Exception {
        continuousIntegrationTestService.testGetBuildStatusBuilding();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthRunning() throws Exception {
        continuousIntegrationTestService.testHealthRunning();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthNotRunning() throws Exception {
        continuousIntegrationTestService.testHealthNotRunning();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testHealthException() throws Exception {

        continuousIntegrationTestService.testHealthException();
    }
}
