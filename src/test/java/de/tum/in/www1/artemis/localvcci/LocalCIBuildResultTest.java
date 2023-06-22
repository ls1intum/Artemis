package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;

class LocalCIBuildResultTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Test
    void testUnsupportedMethods() {
        LocalCIBuildResult localCIBuildResult = new LocalCIBuildResult(null, null, null, true, null, null);

        assertThat(localCIBuildResult.extractBuildLogs(null)).isEmpty();
        assertThat(localCIBuildResult.getTestwiseCoverageReports()).isEmpty();
    }
}
