package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.core.env.Environment;

class ModuleFeatureInfoContributorTest {

    private final Environment mockEnv = mock(Environment.class);

    private ModuleFeatureInfoContributor contributor;

    @Test
    void testAtlasContribution() {
        mockProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, true);

        contributor = new ModuleFeatureInfoContributor(mockEnv);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        var activeModuleFeatures = info.get("activeModuleFeatures");
        assertThat(activeModuleFeatures).isInstanceOf(List.class);

        var activeModuleFeaturesList = (List<String>) activeModuleFeatures;
        assertThat(activeModuleFeaturesList).contains(Constants.MODULE_FEATURE_ATLAS);
    }

    private void mockProperty(String key, Boolean value) {
        when(mockEnv.getProperty(key, Boolean.class)).thenReturn(value);
    }
}
