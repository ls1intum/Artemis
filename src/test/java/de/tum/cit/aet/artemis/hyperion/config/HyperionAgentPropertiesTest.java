package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * One default per configuration key, for the two keys whose relationship is a startup invariant: {@code stale-job-timeout} must strictly exceed {@code max-job-duration} or
 * {@code GenerationJobService#init} fails the node. The shipped yml only binds on a node with the {@code artemis} profile active, so a yml default that disagrees with the Java
 * field is a deployment where one node boots and another dies at startup on the same configuration.
 */
class HyperionAgentPropertiesTest {

    private static final String MAX_JOB_DURATION = "artemis.hyperion.agent.max-job-duration";

    private static final String STALE_JOB_TIMEOUT = "artemis.hyperion.agent.stale-job-timeout";

    @Test
    void shippedConfigurationRestatesTheJavaDefaultsForBothHalvesOfTheStaleJobInvariant() throws IOException {
        // Read from the shipped configuration rather than from a literal, so changing either value in only one of the two places fails here.
        List<PropertySource<?>> shippedConfiguration = artemisConfigurationDeclaring(STALE_JOB_TIMEOUT);
        HyperionAgentProperties javaDefaults = new HyperionAgentProperties();

        assertThat(shippedDuration(shippedConfiguration, MAX_JOB_DURATION)).isEqualTo(javaDefaults.getMaxJobDuration());
        assertThat(shippedDuration(shippedConfiguration, STALE_JOB_TIMEOUT)).isEqualTo(javaDefaults.getStaleJobTimeout());
    }

    @Test
    void theJavaDefaultsAloneSatisfyTheStaleJobInvariant() {
        // On a node where the artemis profile is inactive nothing but these fields is bound, so a pair that only holds once the yml is applied fails startup exactly where there
        // is no yml to read.
        HyperionAgentProperties javaDefaults = new HyperionAgentProperties();

        assertThat(javaDefaults.getStaleJobTimeout()).isGreaterThan(javaDefaults.getMaxJobDuration());
        assertThatCode(() -> HyperionGenerationTimeouts.validateStaleJobTimeout(javaDefaults.getStaleJobTimeout(), javaDefaults.getMaxJobDuration())).doesNotThrowAnyException();
    }

    /** The test classpath shadows config/application-artemis.yml, so select by content: the shipped configuration is the one that declares the property under test. */
    private static List<PropertySource<?>> artemisConfigurationDeclaring(String property) throws IOException {
        List<PropertySource<?>> declaring = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:config/application-artemis.yml")) {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("artemis-config", resource);
            if (sources.stream().anyMatch(source -> source.getProperty(property) != null)) {
                declaring.addAll(sources);
            }
        }
        assertThat(declaring).as("exactly one classpath copy of config/application-artemis.yml declares %s", property).isNotEmpty();
        return declaring;
    }

    private static Duration shippedDuration(List<PropertySource<?>> sources, String property) {
        return sources.stream().map(source -> source.getProperty(property)).filter(Objects::nonNull).findFirst().map(value -> Duration.parse(String.valueOf(value)))
                .orElseThrow(() -> new AssertionError(property + " is not declared in the shipped configuration"));
    }
}
