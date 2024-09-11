package de.tum.cit.aet.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.service.localci.dto.BuildResult;

class BuildResultTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Test
    void testUnsupportedMethods() {
        BuildResult buildResult = new BuildResult(null, null, null, true, null, null, null);

        assertThat(buildResult.extractBuildLogs()).isEmpty();
        assertThat(buildResult.getTestwiseCoverageReports()).isEmpty();
    }
}
