package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;

class HazelcastGenerationFencingTest {

    @Test
    void generationAndLegacyFencingSettingsCannotAddCacheQuorumToDatabaseOwnership() {
        for (int expected : new int[] { 1, 2, 3 }) {
            for (boolean enabled : new boolean[] { false, true }) {
                var env = new MockEnvironment().withProperty("artemis.hyperion.exercise-generation.enabled", String.valueOf(enabled))
                        .withProperty("artemis.hyperion.generation.cluster-fencing-enabled", "true")
                        .withProperty("jhipster.cache.hazelcast.expected-data-member-count", String.valueOf(expected));
                var bean = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class), env,
                        Optional.empty());
                Config config = new Config();
                config.addMapConfig(new MapConfig("default").setBackupCount(1));
                ReflectionTestUtils.invokeMethod(bean, "configureSplitBrainProtection", config);
                assertThat(config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP).getSplitBrainProtectionName()).isNull();
                assertThat(config.getSplitBrainProtectionConfigs()).doesNotContainKey("hyperion-generation-slot-majority");
            }
        }
    }
}
