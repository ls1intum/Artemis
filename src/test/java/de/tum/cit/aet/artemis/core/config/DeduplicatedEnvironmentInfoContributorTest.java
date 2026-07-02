package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.EnvironmentInfoContributor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Test for {@link DeduplicatedEnvironmentInfoContributor}.
 * <p>
 * Reproduces the real-world setup where {@code info.*} properties are defined in camelCase (as in the YAML configs) and additionally overridden by all-uppercase
 * {@code INFO_*} environment variables, and verifies that the resulting {@code /management/info} details contain only the camelCase key (no all-lowercase duplicate).
 */
class DeduplicatedEnvironmentInfoContributorTest {

    /**
     * Builds an environment with a system-environment source (uppercase {@code INFO_*} vars, highest precedence) and a YAML-style camelCase map source, mirroring how
     * Artemis is actually configured in the (multi-node / Playwright) E2E stacks and in production.
     */
    private static StandardEnvironment environmentWith(Map<String, Object> envVars, Map<String, Object> yamlProperties) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        // Drop the JVM's real system sources so the test is deterministic regardless of the machine's actual environment variables.
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        // Environment variables outrank the YAML defaults, exactly as in a real deployment.
        sources.addFirst(new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, envVars));
        sources.addLast(new MapPropertySource("yaml", yamlProperties));
        return environment;
    }

    private static Info contribute(StandardEnvironment environment) {
        Info.Builder builder = new Info.Builder();
        new DeduplicatedEnvironmentInfoContributor(environment).contribute(builder);
        return builder.build();
    }

    @Test
    void shouldCollapseCaseVariantsToCamelCaseKeyWithEnvOverrideValue() {
        Map<String, Object> envVars = new HashMap<>();
        envVars.put("INFO_TESTSERVER", "true");
        envVars.put("INFO_OPERATORNAME", "TUM");

        Map<String, Object> yaml = new HashMap<>();
        yaml.put("info.testServer", false);
        yaml.put("info.operatorName", "");
        yaml.put("info.localLLMDeploymentEnabled", false);
        yaml.put("info.sentry.dsn", "https://example.invalid");

        Info info = contribute(environmentWith(envVars, yaml));

        // The camelCase key survives and carries the environment-variable override value; the all-lowercase duplicate is gone.
        assertThat(info.get("testServer")).isEqualTo("true");
        assertThat(info.get("testserver")).isNull();
        assertThat(info.get("operatorName")).isEqualTo("TUM");
        assertThat(info.get("operatorname")).isNull();

        // Properties present only in YAML (no env-var override) are unaffected and appear exactly once, keeping their original type.
        assertThat(info.get("localLLMDeploymentEnabled")).isEqualTo(false);

        // Nested maps are preserved and recursed into.
        assertThat(info.get("sentry")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> sentry = (Map<String, Object>) info.get("sentry");
        assertThat(sentry).containsEntry("dsn", "https://example.invalid");

        // No all-lowercase case-variant duplicate leaks through.
        assertThat(info.getDetails().keySet()).doesNotContain("testserver", "operatorname");
    }

    @Test
    void shouldPreserveEveryPropertyTheBuiltInEnvironmentContributorExposes() {
        // Mirror the production setup: four info.* properties overridden by INFO_* env vars, the rest defined only in YAML.
        Map<String, Object> envVars = new HashMap<>();
        envVars.put("INFO_TESTSERVER", "true");
        envVars.put("INFO_OPERATORNAME", "TUM");
        envVars.put("INFO_TEXTASSESSMENTANALYTICSENABLED", "true");
        envVars.put("INFO_STUDENTEXAMSTORESESSIONDATA", "true");

        Map<String, Object> yaml = new HashMap<>();
        yaml.put("info.testServer", false);
        yaml.put("info.textAssessmentAnalyticsEnabled", false);
        yaml.put("info.studentExamStoreSessionData", true);
        yaml.put("info.localLLMDeploymentEnabled", false);
        yaml.put("info.operatorName", "");
        yaml.put("info.operatorAdminName", "");
        yaml.put("info.contact", "");
        yaml.put("info.sentry.dsn", "https://example.invalid");

        StandardEnvironment environment = environmentWith(envVars, yaml);

        // The output of Spring's built-in contributor (with duplicates) is the baseline we must not lose information against.
        Info.Builder builtInBuilder = new Info.Builder();
        new EnvironmentInfoContributor(environment).contribute(builtInBuilder);
        Map<String, Object> builtIn = builtInBuilder.build().getDetails();

        Info.Builder dedupedBuilder = new Info.Builder();
        new DeduplicatedEnvironmentInfoContributor(environment).contribute(dedupedBuilder);
        Map<String, Object> deduped = dedupedBuilder.build().getDetails();

        // Sanity check: the built-in contributor really does emit the all-lowercase duplicates we intend to remove.
        assertThat(builtIn).containsKeys("testServer", "testserver", "operatorName", "operatorname");

        // No logical property is lost: the key sets, compared case-insensitively, are identical.
        Set<String> builtInLogicalKeys = builtIn.keySet().stream().map(key -> key.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> dedupedLogicalKeys = deduped.keySet().stream().map(key -> key.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        assertThat(dedupedLogicalKeys).isEqualTo(builtInLogicalKeys);

        // Nothing new is invented, and the only keys dropped are exactly the all-lowercase case-variants.
        assertThat(deduped.keySet()).isSubsetOf(builtIn.keySet());
        Set<String> droppedKeys = builtIn.keySet().stream().filter(key -> !deduped.containsKey(key)).collect(Collectors.toSet());
        assertThat(droppedKeys).containsExactlyInAnyOrder("testserver", "operatorname", "textassessmentanalyticsenabled", "studentexamstoresessiondata");

        // Every surviving key keeps the exact value the built-in contributor produced (including the nested sentry map).
        deduped.forEach((key, value) -> assertThat(value).isEqualTo(builtIn.get(key)));
    }

    @Test
    void shouldKeepLowercaseKeyForPropertySetOnlyViaEnvironmentVariable() {
        Map<String, Object> envVars = new HashMap<>();
        // No camelCase YAML counterpart exists, so the lowercase spelling is the only variant and must be retained.
        envVars.put("INFO_ENVONLYFLAG", "enabled");

        Info info = contribute(environmentWith(envVars, new HashMap<>()));

        assertThat(info.get("envonlyflag")).isEqualTo("enabled");
    }
}
