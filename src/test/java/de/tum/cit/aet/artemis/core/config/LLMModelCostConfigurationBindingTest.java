package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

/** Guards operator-supplied model pricing and normalized managed-deployment keys. */
class LLMModelCostConfigurationBindingTest {

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
    void dottedModelBindsFromEnvironmentVariablesViaStrippedKey() {
        // This is the Docker path: env-var keys cannot contain '.' or '-', so the collection renders
        // "gpt-5.4" as ARTEMIS_LLM_MODELCOSTS_GPT54_*. Prove Spring binds that into model-costs[gpt54].
        var config = bindEnvironment(Map.of("ARTEMIS_LLM_MODELCOSTS_GPT54_INPUTCOSTPERMILLIONEUR", "2.30", "ARTEMIS_LLM_MODELCOSTS_GPT54_OUTPUTCOSTPERMILLIONEUR", "13.80",
                "ARTEMIS_LLM_MODELCOSTS_GPT5MINI_INPUTCOSTPERMILLIONEUR", "0.23", "ARTEMIS_LLM_MODELCOSTS_GPT5MINI_OUTPUTCOSTPERMILLIONEUR", "1.84",
                "ARTEMIS_LLM_MODELCOSTS_GPT5MINI_CACHEDINPUTCOSTPERMILLIONEUR", "0.023"));
        var modelCosts = config.getModelCosts();

        assertThat(modelCosts).containsKey("gpt54");
        assertThat(modelCosts.get("gpt54").getInputCostPerMillionEur()).isCloseTo(2.30f, within(1e-4f));
        assertThat(modelCosts.get("gpt54").getOutputCostPerMillionEur()).isCloseTo(13.80f, within(1e-4f));
        assertThat(modelCosts.get("gpt5mini").getInputCostPerMillionEur()).isCloseTo(0.23f, within(1e-4f));
        assertThat(modelCosts.get("gpt5mini").getCachedInputCostPerMillionEur()).isCloseTo(0.023f, within(1e-4f));
    }

    @Test
    void rejectsModelCostKeyThatStripsToEmpty() {
        var config = new LLMModelCostConfiguration();
        LLMModelCostConfiguration.ModelCostProperties cost = new LLMModelCostConfiguration.ModelCostProperties();
        cost.setInputCostPerMillionEur(1.0f);
        cost.setOutputCostPerMillionEur(2.0f);
        config.setModelCosts(new HashMap<>(Map.of("...", cost)));

        assertThatThrownBy(config::validateModelCosts).isInstanceOf(IllegalStateException.class).hasMessageContaining("alphanumeric");
    }

    @Test
    void rejectsNegativeAndNonFinitePrices() {
        var config = new LLMModelCostConfiguration();
        LLMModelCostConfiguration.ModelCostProperties cost = new LLMModelCostConfiguration.ModelCostProperties();
        cost.setInputCostPerMillionEur(-0.01f);
        cost.setOutputCostPerMillionEur(Float.NaN);
        config.setModelCosts(Map.of("model", cost));

        assertThatThrownBy(config::validateModelCosts).isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid input price");
        cost.setInputCostPerMillionEur(1.0f);
        assertThatThrownBy(config::validateModelCosts).isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid output price");
    }

    @Test
    void productionEnvironmentPricesHaveUniqueNormalizedKeys() {
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
        var strippedKeys = config.getModelCosts().keySet().stream().map(LLMModelCostConfiguration::stripToAlphanumeric).toList();

        assertThat(strippedKeys).doesNotHaveDuplicates();
        assertThat(config.getModelCosts()).containsKeys("gpt54", "gpt5mini", "gpt55");
    }

    @Test
    void shippedConfigurationBindsTheDeployedModelKeyVerbatim() throws IOException {
        // The bracket-quoted key in application-artemis.yml is only useful if Spring keeps the slash, the dot and
        // the capitals. Relaxed binding would fold them away, and stripToAlphanumeric does not lower-case, so a
        // folded key would silently never match the identifier the provider reports.
        var modelCosts = bind(shippedConfiguration()).getModelCosts();

        assertThat(modelCosts).containsKey("Qwen/Qwen3.8-27B");
        assertThat(modelCosts.get("Qwen/Qwen3.8-27B").getInputCostPerMillionEur()).isCloseTo(0.2764f, within(1e-5f));
        assertThat(modelCosts.get("Qwen/Qwen3.8-27B").getOutputCostPerMillionEur()).isCloseTo(2.1593f, within(1e-5f));
        assertThat(modelCosts.get("Qwen/Qwen3.8-27B").getCachedInputCostPerMillionEur()).isCloseTo(0.0276f, within(1e-5f));
    }

    @Test
    void shippedConfigurationKeysAreValidAndUniqueOnceStripped() throws IOException {
        var config = bind(shippedConfiguration());

        // Both lookup paths LLMTokenUsageService uses must stay well-formed for whatever the file ships.
        config.validateModelCosts();
        assertThat(config.getModelCosts().keySet().stream().map(LLMModelCostConfiguration::stripToAlphanumeric).toList()).doesNotHaveDuplicates();
        assertThat(LLMModelCostConfiguration.stripToAlphanumeric("Qwen/Qwen3.8-27B")).isEqualTo("QwenQwen3827B");
    }

    /**
     * The configuration file that actually ships.
     * <p>
     * Deliberately read from the source tree rather than from the classpath: {@code src/test/resources} carries its own
     * {@code config/application-artemis.yml}, which shadows the production one for every test, so a
     * {@code ClassPathResource} here would silently assert against the test fixture and pass while the deployment is unpriced.
     */
    private static Resource shippedConfiguration() {
        return new FileSystemResource("src/main/resources/config/application-artemis.yml");
    }

    private static void putEnvCost(Map<String, Object> environment, String key, String input, String output) {
        environment.put("ARTEMIS_LLM_MODELCOSTS_" + key + "_INPUTCOSTPERMILLIONEUR", input);
        environment.put("ARTEMIS_LLM_MODELCOSTS_" + key + "_OUTPUTCOSTPERMILLIONEUR", output);
    }
}
