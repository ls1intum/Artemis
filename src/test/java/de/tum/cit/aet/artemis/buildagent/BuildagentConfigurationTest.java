package de.tum.cit.aet.artemis.buildagent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

public class BuildagentConfigurationTest extends AbstractArtemisBuildAgentTest {

    @Autowired
    private Environment environment;

    private ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();

    @Test
    void testAtlasDisabled() {
        boolean atlasEnabled = artemisConfigHelper.isAtlasEnabled(environment);
        assertThat(atlasEnabled).isFalse();
    }
}
