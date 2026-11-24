package de.tum.cit.aet.artemis.atlas.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AtlasOrchestratorPropertiesBindingTest {

    @Test
    void retiredModelSettingsWithAnUnresolvablePlaceholderDoNotStopStartup() {
        new ApplicationContextRunner().withUserConfiguration(Binding.class).withPropertyValues("artemis.atlas.orchestrator.temperature=${RETIRED_ATLAS_TEMPERATURE}",
                "artemis.atlas.orchestrator.responses-api-enabled=${RETIRED_ATLAS_RESPONSES}", "artemis.atlas.orchestrator.model=gpt-5.6-luna").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AtlasOrchestratorProperties.class).debounceWindowSeconds()).isEqualTo(1800);
                });
    }

    @EnableConfigurationProperties(AtlasOrchestratorProperties.class)
    static class Binding {
    }
}
