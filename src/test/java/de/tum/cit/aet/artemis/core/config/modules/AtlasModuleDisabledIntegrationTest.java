package de.tum.cit.aet.artemis.core.config.modules;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.atlas.config.AtlasNotPresentException;
import de.tum.cit.aet.artemis.core.service.export.DataExportScienceEventService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVcSamlTest;

/**
 * Test class to check behavior when the Atlas module is disabled.
 */
class AtlasModuleDisabledIntegrationTest extends AbstractSpringIntegrationLocalVcSamlTest {

    @Autowired
    private DataExportScienceEventService dataExportScienceEventService;

    @Test
    void testDataExportThrowsExceptionWhenAtlasIsDisabled() {
        assertThatThrownBy(() -> dataExportScienceEventService.createScienceEventExport(null, null)).isInstanceOf(AtlasNotPresentException.class);
    }
}
