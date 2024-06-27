package de.tum.in.www1.artemis.versioning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;

class VersioningIndependentTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private VersioningTestService versioningTestService;

    @Test
    void testDuplicateRoutes() {
        versioningTestService.testDuplicateRoutes();
    }
}
