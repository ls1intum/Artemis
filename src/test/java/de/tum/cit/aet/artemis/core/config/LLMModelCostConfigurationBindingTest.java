package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * Guards the binding of {@code artemis.llm.model-costs} from the production {@code application-artemis.yml}.
 * Map keys that contain a dot (e.g. {@code gpt-5.4}) must be bracket-quoted in YAML, otherwise Spring
 * relaxed binding treats the dot as a nested path and the key never lands in the map — which silently
 * costs that model at 0 € and breaks the per-course LLM cost view (Atlas orchestrator runs on {@code gpt-5.4}).
 * <p>
 * The production file is read directly from the source tree (not the classpath) because the test resources
 * ship their own {@code application-artemis.yml} that would otherwise shadow it.
 */
class LLMModelCostConfigurationBindingTest {

    private static final String PRODUCTION_CONFIG = "src/main/resources/config/application-artemis.yml";

    private LLMModelCostConfiguration bindFromProductionConfig() throws IOException {
        Resource resource = new FileSystemResource(PRODUCTION_CONFIG);
        assertThat(resource.exists()).as("production config %s must be resolvable from the working directory", PRODUCTION_CONFIG).isTrue();

        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application-artemis", resource);
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        return binder.bind("artemis.llm", LLMModelCostConfiguration.class).orElseGet(LLMModelCostConfiguration::new);
    }

    @Test
    void dottedModelCostKeysBindFromProductionConfig() throws IOException {
        var modelCosts = bindFromProductionConfig().getModelCosts();

        // Sanity: the dot-free key has always bound correctly.
        assertThat(modelCosts).containsKey("gpt-5-mini");

        // The regression: dotted keys must bind as their literal model name (requires bracket-quoting in YAML).
        assertThat(modelCosts).containsKey("gpt-5.4");
        assertThat(modelCosts.get("gpt-5.4").getInputCostPerMillionEur()).isCloseTo(2.30f, within(1e-4f));
        assertThat(modelCosts.get("gpt-5.4").getOutputCostPerMillionEur()).isCloseTo(13.80f, within(1e-4f));

        assertThat(modelCosts).containsKey("gpt-5.4-mini");
        assertThat(modelCosts.get("gpt-5.4-mini").getInputCostPerMillionEur()).isCloseTo(0.69f, within(1e-4f));
        assertThat(modelCosts.get("gpt-5.4-mini").getOutputCostPerMillionEur()).isCloseTo(4.14f, within(1e-4f));

        // The mangled-path form must NOT appear (would mean the dot was parsed as a nested path).
        assertThat(modelCosts).doesNotContainKey("gpt-5");
    }
}
