package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
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

    private LLMModelCostConfiguration bindEnvironment(Map<String, Object> environment) {
        MutablePropertySources propertySources = new MutablePropertySources();
        // SystemEnvironmentPropertySource applies the exact same relaxed-binding name translation that
        // Spring uses for real OS environment variables (and Docker env files), so this reproduces a
        // Docker deployment's binding rather than just simulating it.
        propertySources.addLast(new SystemEnvironmentPropertySource("systemEnvironment", environment));
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        return binder.bind("artemis.llm", LLMModelCostConfiguration.class).orElseGet(LLMModelCostConfiguration::new);
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
    void defaultsAreNotSeededWhenConfiguredUnderStrippedKey() throws IOException {
        // Docker env-var config produces the stripped key "gpt54" for the model "gpt-5.4". The "gpt-5.4"
        // default must not also be seeded, otherwise both keys would strip to "gpt54" and collide.
        var config = bindYaml("""
                artemis:
                  llm:
                    model-costs:
                      gpt54:
                        input-cost-per-million-eur: 9.99
                        output-cost-per-million-eur: 11.11
                """);
        config.applyDefaultModelCosts();
        var modelCosts = config.getModelCosts();

        assertThat(modelCosts).containsKey("gpt54");
        assertThat(modelCosts).doesNotContainKey("gpt-5.4");
        assertThat(modelCosts.get("gpt54").getInputCostPerMillionEur()).isCloseTo(9.99f, within(1e-4f));
        // an unrelated default is still seeded
        assertThat(modelCosts).containsKey("gpt-5.4-mini");
    }

    @Test
    void dottedModelBindsFromEnvironmentVariablesViaStrippedKey() {
        // This is the Docker path: env-var keys cannot contain '.' or '-', so the collection renders
        // "gpt-5.4" as ARTEMIS_LLM_MODELCOSTS_GPT54_*. Prove Spring binds that into model-costs[gpt54].
        var config = bindEnvironment(Map.of("ARTEMIS_LLM_MODELCOSTS_GPT54_INPUTCOSTPERMILLIONEUR", "2.30", "ARTEMIS_LLM_MODELCOSTS_GPT54_OUTPUTCOSTPERMILLIONEUR", "13.80",
                "ARTEMIS_LLM_MODELCOSTS_GPT5MINI_INPUTCOSTPERMILLIONEUR", "0.23", "ARTEMIS_LLM_MODELCOSTS_GPT5MINI_OUTPUTCOSTPERMILLIONEUR", "1.84"));
        var modelCosts = config.getModelCosts();

        assertThat(modelCosts).containsKey("gpt54");
        assertThat(modelCosts.get("gpt54").getInputCostPerMillionEur()).isCloseTo(2.30f, within(1e-4f));
        assertThat(modelCosts.get("gpt54").getOutputCostPerMillionEur()).isCloseTo(13.80f, within(1e-4f));
        assertThat(modelCosts.get("gpt5mini").getInputCostPerMillionEur()).isCloseTo(0.23f, within(1e-4f));
    }

    @Test
    void dottedModelFromEnvironmentSurvivesDefaultSeeding() {
        // The env key "gpt54" and the seeded default "gpt-5.4" both strip to "gpt54"; seeding must skip
        // the default so the two don't later collide at lookup-map construction. Guards the Docker boot path.
        var config = bindEnvironment(Map.of("ARTEMIS_LLM_MODELCOSTS_GPT54_INPUTCOSTPERMILLIONEUR", "2.30", "ARTEMIS_LLM_MODELCOSTS_GPT54_OUTPUTCOSTPERMILLIONEUR", "13.80"));
        config.applyDefaultModelCosts();
        var modelCosts = config.getModelCosts();

        assertThat(modelCosts).containsKey("gpt54");
        assertThat(modelCosts).doesNotContainKey("gpt-5.4");
        assertThat(modelCosts.get("gpt54").getInputCostPerMillionEur()).isCloseTo(2.30f, within(1e-4f));
        // unrelated default still present
        assertThat(modelCosts).containsKey("gpt-5.4-mini");
    }

    @Test
    void productionConfigBindsWithoutActiveModelCostsAndSeedsCodeDefaults() throws IOException {
        // The bundled config must not ship an active model-costs entry (a literal key there would collide
        // with the same model supplied via env vars); the baselines come from the code defaults instead.
        var config = bind(new FileSystemResource(PRODUCTION_CONFIG));
        assertThat(config.getModelCosts()).isEmpty();

        config.applyDefaultModelCosts();
        assertThat(config.getModelCosts()).containsKeys("gpt-5-mini", "gpt-5.4", "gpt-5.4-mini");
    }

    @Test
    void productionEnvironmentDeploymentBootsWithoutKeyCollision() {
        // Reproduces the full Docker scenario: the bundled config seeds nothing active, the collection
        // supplies the whole model set via env vars (stripped keys), and code defaults are applied on top.
        // Every stripped key must stay unique so lookup-map construction in LLMTokenUsageService cannot throw.
        Map<String, Object> environment = new HashMap<>();
        putEnvCost(environment, "GPT5MINI", "0.23", "1.84");
        putEnvCost(environment, "GPT5NANO", "0.046", "0.37");
        putEnvCost(environment, "GPT51", "1.15", "9.20");
        putEnvCost(environment, "GPT52", "1.61", "12.88");
        putEnvCost(environment, "GPT53", "1.61", "12.88");
        putEnvCost(environment, "GPT54MINI", "0.69", "4.14");
        putEnvCost(environment, "GPT54NANO", "0.18", "1.15");
        putEnvCost(environment, "GPT54", "2.30", "13.80");
        putEnvCost(environment, "GPT55", "4.60", "27.60");

        var config = bindEnvironment(environment);
        config.applyDefaultModelCosts();
        var strippedKeys = config.getModelCosts().keySet().stream().map(LLMModelCostConfiguration::stripToAlphanumeric).toList();

        assertThat(strippedKeys).doesNotHaveDuplicates();
        assertThat(config.getModelCosts()).containsKeys("gpt54", "gpt5mini", "gpt55");
    }

    private static void putEnvCost(Map<String, Object> environment, String key, String input, String output) {
        environment.put("ARTEMIS_LLM_MODELCOSTS_" + key + "_INPUTCOSTPERMILLIONEUR", input);
        environment.put("ARTEMIS_LLM_MODELCOSTS_" + key + "_OUTPUTCOSTPERMILLIONEUR", output);
    }
}
