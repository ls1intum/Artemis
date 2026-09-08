package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SplitBrainProtectionConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.splitbrainprotection.SplitBrainProtectionOn;

class HazelcastConfigurationTest {

    private static final String SPLIT_BRAIN_PROTECTION_NAME = "artemis-split-brain-protection";

    /**
     * Every Hazelcast map name Hyperion coordinates through, maintained as an explicit inventory rather than derived from the configuration under test. A map that Hyperion code
     * uses but nobody registers silently inherits the default LRU-evicting map config; evicting a live entry there is a correctness failure, not a cache miss.
     */
    private static final Set<String> HYPERION_CORRECTNESS_MAPS = Set.of("hyperion-provider-failure-cooldowns", "hyperion-exercise-generation-jobs",
            "hyperion-exercise-generation-cancellations", "hyperion-exercise-generation-transcripts", "hyperion-exercise-generation-file-changes",
            "hyperion-exercise-generation-usage", "hyperion-generation-token-budget-reservations", "hyperion-exercise-generation-baselines",
            "hyperion-exercise-generation-artifacts", "hyperion-sandbox-payloads");

    @Test
    void shouldProtectHyperionCorrectnessMapsAgainstMemberAndNetworkFailure() {
        HazelcastConfiguration hazelcastConfiguration = createHazelcastConfiguration();
        ArtemisProperties artemisProperties = new ArtemisProperties();
        artemisProperties.getCache().getHazelcast().setBackupCount(0);
        artemisProperties.getCache().getHazelcast().setExpectedDataMemberCount(3);
        Config config = new Config();

        ReflectionTestUtils.invokeMethod(hazelcastConfiguration, "configureCacheMaps", config, artemisProperties);
        ReflectionTestUtils.invokeMethod(hazelcastConfiguration, "configureSplitBrainProtection", config, true, artemisProperties);

        // getMapConfigOrNull applies the same name/wildcard resolution Hazelcast performs at runtime, but unlike getMapConfig it does not register a fallback entry as a side
        // effect, so the closure assertion below still sees only the maps the configuration itself declared.
        for (String mapName : HYPERION_CORRECTNESS_MAPS) {
            assertThat(config.getMapConfigOrNull(mapName)).as("%s must resolve to an explicitly declared MapConfig, not the evicting default", mapName).isNotNull();
        }
        // The inventory is closed in both directions: a Hyperion map added to the configuration without being added here would otherwise escape every assertion in this class.
        assertThat(config.getMapConfigs().keySet().stream().filter(mapName -> mapName.startsWith("hyperion-")).toList())
                .containsExactlyInAnyOrderElementsOf(HYPERION_CORRECTNESS_MAPS);

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

    @ParameterizedTest(name = "hyperion={0}, exerciseGeneration={1}, core={2}, localci={3}, localvc={4} => capability={5}")
    @CsvSource({ "true,  true,  true,  true,  true,  true", "false, true,  true,  true,  true,  false", "true,  false, true,  true,  true,  false",
            "true,  true,  false, true,  true,  false", "true,  true,  true,  false, true,  false", "true,  true,  true,  true,  false, false" })
    void shouldAdvertiseExerciseGenerationCapabilityFromTheExactFeaturePredicate(boolean hyperionEnabled, boolean exerciseGenerationEnabled, boolean coreProfileActive,
            boolean localCiProfileActive, boolean localVcProfileActive, boolean expectedCapability) {
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, Boolean.toString(hyperionEnabled))
                .withProperty(Constants.HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, Boolean.toString(exerciseGenerationEnabled));
        List<String> profiles = new ArrayList<>();
        if (coreProfileActive) {
            profiles.add(PROFILE_CORE);
        }
        if (localCiProfileActive) {
            profiles.add(PROFILE_LOCALCI);
        }
        if (localVcProfileActive) {
            profiles.add(PROFILE_LOCALVC);
        }
        environment.setActiveProfiles(profiles.toArray(String[]::new));
        HazelcastConfiguration hazelcastConfiguration = createHazelcastConfiguration(environment);
        Config config = new Config();

        ReflectionTestUtils.invokeMethod(hazelcastConfiguration, "configureMemberAttributes", config);

        assertThat(config.getMemberAttributeConfig().getAttribute(HazelcastConfiguration.HYPERION_EXERCISE_GENERATION_CAPABLE_MEMBER_ATTRIBUTE))
                .isEqualTo(Boolean.toString(expectedCapability));
    }

    @ParameterizedTest
    @CsvSource({ "1, 1", "2, 2", "3, 2", "4, 3" })
    void shouldConfigureMajorityFromExpectedDataMemberCount(int expectedDataMemberCount, int expectedMajority) {
        assertThat(HazelcastConfiguration.majorityForExpectedDataMemberCount(expectedDataMemberCount)).isEqualTo(expectedMajority);
    }

    @Test
    void shouldRejectInvalidExpectedDataMemberCount() {
        HazelcastConfiguration hazelcastConfiguration = createHazelcastConfiguration();
        ArtemisProperties artemisProperties = new ArtemisProperties();
        artemisProperties.getCache().getHazelcast().setExpectedDataMemberCount(0);

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(hazelcastConfiguration, "configureSplitBrainProtection", new Config(), true, artemisProperties))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expected-data-member-count");
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
        return createHazelcastConfiguration(new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "false"));
    }

    private HazelcastConfiguration createHazelcastConfiguration(Environment environment) {
        return new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class), environment,
                Optional.empty());
    }
}
