package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildResult;

class BuildResultTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Test
    void testUnsupportedMethods() {
        BuildResult buildResult = new BuildResult(null, null, null, true, null, null, null);

        assertThat(buildResult.extractBuildLogs()).isEmpty();
        assertThat(buildResult.getTestwiseCoverageReports()).isEmpty();
    }
}
