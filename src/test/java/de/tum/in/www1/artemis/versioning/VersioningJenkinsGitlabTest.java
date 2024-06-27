package de.tum.in.www1.artemis.versioning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;

class VersioningJenkinsGitlabTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private VersioningTestService versioningTestService;

    @Test
    void testDuplicateRoutes() {
        versioningTestService.testDuplicateRoutes();
    }
}
