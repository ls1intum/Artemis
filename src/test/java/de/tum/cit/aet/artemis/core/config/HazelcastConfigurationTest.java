package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SplitBrainProtectionConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.splitbrainprotection.SplitBrainProtectionOn;

class HazelcastConfigurationTest {

    private static final String SPLIT_BRAIN_PROTECTION_NAME = "artemis-split-brain-protection";

    private static final Set<String> HYPERION_CORRECTNESS_MAPS = Set.of("hyperion-provider-failure-cooldowns", "hyperion-exercise-generation-jobs",
            "hyperion-exercise-generation-cancellations", "hyperion-exercise-generation-transcripts", "hyperion-exercise-generation-file-changes",
            "hyperion-generation-token-budget-reservations", "hyperion-exercise-generation-baselines", "hyperion-sandbox-payloads");

    @Test
    void shouldProtectHyperionCorrectnessMapsAgainstMemberAndNetworkFailure() {
        HazelcastConfiguration hazelcastConfiguration = createHazelcastConfiguration();
        ArtemisProperties artemisProperties = new ArtemisProperties();
        artemisProperties.getCache().getHazelcast().setBackupCount(0);
        Config config = new Config();

        ReflectionTestUtils.invokeMethod(hazelcastConfiguration, "configureCacheMaps", config, artemisProperties);
        ReflectionTestUtils.invokeMethod(hazelcastConfiguration, "configureSplitBrainProtection", config, true);

        SplitBrainProtectionConfig protectionConfig = config.getSplitBrainProtectionConfigs().get(SPLIT_BRAIN_PROTECTION_NAME);
        assertThat(protectionConfig).isNotNull();
        assertThat(protectionConfig.isEnabled()).isTrue();
        assertThat(protectionConfig.getMinimumClusterSize()).isEqualTo(2);
        assertThat(protectionConfig.getProtectOn()).isEqualTo(SplitBrainProtectionOn.READ_WRITE);

        assertThat(config.getMapConfigs()).containsKeys(HYPERION_CORRECTNESS_MAPS.toArray(String[]::new));
        for (String mapName : HYPERION_CORRECTNESS_MAPS) {
            MapConfig mapConfig = config.getMapConfigs().get(mapName);
            assertThat(mapConfig.getBackupCount()).as("synchronous backup count for %s", mapName).isGreaterThanOrEqualTo(1);
            assertThat(mapConfig.getSplitBrainProtectionName()).as("split-brain protection for %s", mapName).isEqualTo(SPLIT_BRAIN_PROTECTION_NAME);
        }
    }

    @Test
    void shouldAllowProtectedMapOperationsInIsolatedTestInstance() {
        HazelcastConfiguration hazelcastConfiguration = createHazelcastConfiguration();
        ReflectionTestUtils.setField(hazelcastConfiguration, "instanceName", "hazelcast-configuration-test-" + UUID.randomUUID());

        HazelcastInstance hazelcastInstance = ReflectionTestUtils.invokeMethod(hazelcastConfiguration, "createTestHazelcastInstance", new ArtemisProperties());
        try {
            SplitBrainProtectionConfig protectionConfig = hazelcastInstance.getConfig().getSplitBrainProtectionConfigs().get(SPLIT_BRAIN_PROTECTION_NAME);
            assertThat(protectionConfig).isNotNull();
            assertThat(protectionConfig.isEnabled()).isFalse();
            assertThatCode(() -> hazelcastInstance.getMap("hyperion-exercise-generation-jobs").put("exercise-1", "job-1")).doesNotThrowAnyException();
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    private HazelcastConfiguration createHazelcastConfiguration() {
        return new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class), mock(Environment.class),
                Optional.empty());
    }
}
