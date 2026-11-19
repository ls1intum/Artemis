package de.tum.cit.aet.artemis.hyperion.config;

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
class GenerationModelCostConfigurationBindingTest {

    private GenerationModelCostConfiguration bind(Resource resource) throws IOException {
        assertThat(resource.exists()).as("config resource must be resolvable").isTrue();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("model-costs-test", resource);
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        return binder.bind("artemis.llm", GenerationModelCostConfiguration.class).orElseGet(GenerationModelCostConfiguration::new);
    }

    private GenerationModelCostConfiguration bindYaml(String yaml) throws IOException {
        return bind(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private GenerationModelCostConfiguration bindEnvironment(Map<String, Object> environment) {
        MutablePropertySources propertySources = new MutablePropertySources();
        // SystemEnvironmentPropertySource applies the exact same relaxed-binding name translation that
        // Spring uses for real OS environment variables (and Docker env files), so this reproduces a
        // Docker deployment's binding rather than just simulating it.
        propertySources.addLast(new SystemEnvironmentPropertySource("systemEnvironment", environment));
        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        return binder.bind("artemis.llm", GenerationModelCostConfiguration.class).orElseGet(GenerationModelCostConfiguration::new);
    }

    @Test
    void dottedKeysRequireBracketQuoting() throws IOException {
        var modelCosts = bindYaml("""
                artemis:
                  llm:
                    model-costs:
                      "[gpt-9.9]":
                        input-cost-per-million-usd: 1.23
                        output-cost-per-million-usd: 4.56
                """).getModelCosts();

        assertThat(modelCosts).containsKey("gpt-9.9");
        assertThat(modelCosts).doesNotContainKey("gpt-9");
    }

    @Test
    void usdRatesBindWithoutReinterpretingLegacyPrices() throws IOException {
        var configuration = bindYaml("""
                artemis:
                  llm:
                    model-costs:
                      '[model.example]':
                        input-cost-per-million-usd: 2.0
                        cached-input-cost-per-million-usd: 0.5
                        output-cost-per-million-usd: 4.0
                        input-cost-per-million-eur: 1.8
                """);
        configuration.validateModelCosts();
        var model = configuration.getModelCosts().get("model.example");
        assertThat(model.getInputCostPerMillionUsd()).isEqualTo(2f);
        assertThat(model.getCachedInputCostPerMillionUsd()).isEqualTo(0.5f);
        assertThat(model.getOutputCostPerMillionUsd()).isEqualTo(4f);
    }

    @Test
    void invalidUsdRateFailsConfigurationRatherThanCreatingAFreeEstimate() {
        var configuration = new GenerationModelCostConfiguration();
        var model = new GenerationModelCostConfiguration.ModelCostProperties();
        model.setInputCostPerMillionUsd(Float.NaN);
        configuration.setModelCosts(Map.of("model", model));
        assertThatThrownBy(configuration::validateModelCosts).isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid USD input price");
    }

    @Test
    void dottedModelBindsFromEnvironmentVariablesViaStrippedKey() {
        // This is the Docker path: env-var keys cannot contain '.' or '-', so the collection renders
        // "gpt-5.4" as ARTEMIS_LLM_MODELCOSTS_GPT54_*. Prove Spring binds that into model-costs[gpt54].
        var config = bindEnvironment(Map.of("ARTEMIS_LLM_MODELCOSTS_GPT54_INPUTCOSTPERMILLIONUSD", "2.30", "ARTEMIS_LLM_MODELCOSTS_GPT54_OUTPUTCOSTPERMILLIONUSD", "13.80",
                "ARTEMIS_LLM_MODELCOSTS_GPT5MINI_INPUTCOSTPERMILLIONUSD", "0.23", "ARTEMIS_LLM_MODELCOSTS_GPT5MINI_OUTPUTCOSTPERMILLIONUSD", "1.84",
                "ARTEMIS_LLM_MODELCOSTS_GPT5MINI_CACHEDINPUTCOSTPERMILLIONUSD", "0.023"));
        var modelCosts = config.getModelCosts();

        assertThat(modelCosts).containsKey("gpt54");
        assertThat(modelCosts.get("gpt54").getInputCostPerMillionUsd()).isCloseTo(2.30f, within(1e-4f));
        assertThat(modelCosts.get("gpt54").getOutputCostPerMillionUsd()).isCloseTo(13.80f, within(1e-4f));
        assertThat(modelCosts.get("gpt5mini").getInputCostPerMillionUsd()).isCloseTo(0.23f, within(1e-4f));
        assertThat(modelCosts.get("gpt5mini").getCachedInputCostPerMillionUsd()).isCloseTo(0.023f, within(1e-4f));
    }

    @Test
    void rejectsModelCostKeyThatStripsToEmpty() {
        var config = new GenerationModelCostConfiguration();
        GenerationModelCostConfiguration.ModelCostProperties cost = new GenerationModelCostConfiguration.ModelCostProperties();
        cost.setInputCostPerMillionUsd(1.0f);
        cost.setOutputCostPerMillionUsd(2.0f);
        config.setModelCosts(new HashMap<>(Map.of("...", cost)));

        assertThatThrownBy(config::validateModelCosts).isInstanceOf(IllegalStateException.class).hasMessageContaining("alphanumeric");
    }

    @Test
    void rejectsNegativeAndNonFinitePrices() {
        var config = new GenerationModelCostConfiguration();
        GenerationModelCostConfiguration.ModelCostProperties cost = new GenerationModelCostConfiguration.ModelCostProperties();
        cost.setInputCostPerMillionUsd(-0.01f);
        cost.setOutputCostPerMillionUsd(Float.NaN);
        config.setModelCosts(Map.of("model", cost));

        assertThatThrownBy(config::validateModelCosts).isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid USD input price");
        cost.setInputCostPerMillionUsd(1.0f);
        assertThatThrownBy(config::validateModelCosts).isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid USD output price");
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
        var strippedKeys = config.getModelCosts().keySet().stream().map(GenerationModelCostConfiguration::stripToAlphanumeric).toList();

        assertThat(strippedKeys).doesNotHaveDuplicates();
        assertThat(config.getModelCosts()).containsKeys("gpt54", "gpt5mini", "gpt55");
    }

    @Test
    void shippedConfigurationDoesNotInventProviderPrices() throws IOException {
        assertThat(bind(shippedConfiguration()).getModelCosts()).isEmpty();
    }

    @Test
    void shippedConfigurationKeysAreValidAndUniqueOnceStripped() throws IOException {
        var config = bind(shippedConfiguration());

        // Exact and normalized model lookups must accept the shipped configuration.
        config.validateModelCosts();
        assertThat(config.getModelCosts().keySet().stream().map(GenerationModelCostConfiguration::stripToAlphanumeric).toList()).doesNotHaveDuplicates();
        assertThat(GenerationModelCostConfiguration.stripToAlphanumeric("Qwen/Qwen3.8-27B")).isEqualTo("QwenQwen3827B");
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
        environment.put("ARTEMIS_LLM_MODELCOSTS_" + key + "_INPUTCOSTPERMILLIONUSD", input);
        environment.put("ARTEMIS_LLM_MODELCOSTS_" + key + "_OUTPUTCOSTPERMILLIONUSD", output);
    }
}
