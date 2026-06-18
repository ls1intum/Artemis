package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * Guards {@link LLMModelCostConfiguration}: baseline Atlas costs are seeded as code defaults (so they survive a
 * managed deployment that overrides the map), configured entries override those defaults, and dotted map keys
 * added to YAML must be bracket-quoted (otherwise relaxed binding parses the dot as a nested path).
 */
class LLMModelCostConfigurationBindingTest {

    private static final String PRODUCTION_CONFIG = "src/main/resources/config/application-artemis.yml";

    private LLMModelCostConfiguration bind(Resource resource) throws IOException {
        assertThat(resource.exists()).as("config resource must be resolvable").isTrue();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("model-costs-test", resource);
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        return binder.bind("artemis.llm", LLMModelCostConfiguration.class).orElseGet(LLMModelCostConfiguration::new);
    }

    private LLMModelCostConfiguration bindYaml(String yaml) throws IOException {
        return bind(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void seedsAtlasModelCostDefaults() {
        var config = new LLMModelCostConfiguration();
        config.applyDefaultModelCosts();
        var modelCosts = config.getModelCosts();

        assertThat(modelCosts.get("gpt-5.4").getInputCostPerMillionEur()).isCloseTo(2.30f, within(1e-4f));
        assertThat(modelCosts.get("gpt-5.4").getOutputCostPerMillionEur()).isCloseTo(13.80f, within(1e-4f));
        assertThat(modelCosts.get("gpt-5.4-mini").getInputCostPerMillionEur()).isCloseTo(0.69f, within(1e-4f));
        assertThat(modelCosts.get("gpt-5.4-mini").getOutputCostPerMillionEur()).isCloseTo(4.14f, within(1e-4f));
    }

    @Test
    void configuredCostsOverrideDefaults() throws IOException {
        var config = bindYaml("""
                artemis:
                  llm:
                    model-costs:
                      "[gpt-5.4]":
                        input-cost-per-million-eur: 9.99
                        output-cost-per-million-eur: 11.11
                """);
        config.applyDefaultModelCosts();
        var modelCosts = config.getModelCosts();

        assertThat(modelCosts.get("gpt-5.4").getInputCostPerMillionEur()).isCloseTo(9.99f, within(1e-4f));
        assertThat(modelCosts.get("gpt-5.4").getOutputCostPerMillionEur()).isCloseTo(11.11f, within(1e-4f));
        assertThat(modelCosts.get("gpt-5.4-mini").getInputCostPerMillionEur()).isCloseTo(0.69f, within(1e-4f));
    }

    @Test
    void dottedKeysRequireBracketQuoting() throws IOException {
        var modelCosts = bindYaml("""
                artemis:
                  llm:
                    model-costs:
                      "[gpt-9.9]":
                        input-cost-per-million-eur: 1.23
                        output-cost-per-million-eur: 4.56
                """).getModelCosts();

        assertThat(modelCosts).containsKey("gpt-9.9");
        assertThat(modelCosts).doesNotContainKey("gpt-9");
    }

    @Test
    void productionConfigBinds() throws IOException {
        var modelCosts = bind(new FileSystemResource(PRODUCTION_CONFIG)).getModelCosts();
        assertThat(modelCosts).containsKey("gpt-5-mini");
    }
}
