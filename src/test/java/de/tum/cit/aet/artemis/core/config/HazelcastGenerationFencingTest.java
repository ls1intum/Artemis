package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.splitbrainprotection.SplitBrainProtectionException;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

class HazelcastGenerationFencingTest {

    @Test
    void disabledGenerationKeepsOrdinarySlotAccessUnprotected() {
        Config config = configuration(new MockEnvironment());
        assertThat(config.getMemberAttributeConfig().getAttribute(DistributedDataProvider.WRITER_FENCING_MEMBER_ATTRIBUTE)).isEqualTo("false");
        var slots = config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP);
        assertThat(slots.getSplitBrainProtectionName()).isNull();
        assertThat(slots.getTimeToLiveSeconds()).isZero();
        assertThat(slots.getEvictionConfig().getEvictionPolicy()).isEqualTo(EvictionPolicy.NONE);
    }

    @Test
    void disabledGenerationCanCreateAndUseTheRealSlotMap() {
        Config config = configuration(new MockEnvironment());
        config.setClusterName("disabled-generation-map-" + UUID.randomUUID());
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);
        var member = Hazelcast.newHazelcastInstance(config);
        try {
            var slots = member.<String, String>getMap(Constants.HYPERION_GENERATION_JOBS_MAP);
            slots.put("42", "ordinary mutation");
            assertThat(slots.get("42")).isEqualTo("ordinary mutation");
        }
        finally {
            member.getLifecycleService().terminate();
        }
    }

    @Test
    void disabledGeneratorCanAdvertiseDeploymentFencingAndProtectItsSlots() {
        var env = new MockEnvironment().withProperty("artemis.hyperion.exercise-generation.enabled", "false")
                .withProperty("artemis.hyperion.generation.cluster-fencing-enabled", "true").withProperty("jhipster.cache.hazelcast.expected-data-member-count", "3");
        Config config = configuration(env);
        assertThat(config.getMemberAttributeConfig().getAttribute(DistributedDataProvider.WRITER_FENCING_MEMBER_ATTRIBUTE)).isEqualTo("true");
        var slots = config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP);
        assertThat(slots.getSplitBrainProtectionName()).isEqualTo("hyperion-generation-slot-majority");
        assertThat(config.getSplitBrainProtectionConfig(slots.getSplitBrainProtectionName()).isEnabled()).isTrue();
        assertThat(config.getSplitBrainProtectionConfig(slots.getSplitBrainProtectionName()).getMinimumClusterSize()).isEqualTo(2);
        assertThat(slots.getEvictionConfig().getEvictionPolicy()).isEqualTo(EvictionPolicy.NONE);
    }

    @Test
    void enabledGenerationDefaultsToDeploymentFencing() {
        Config config = configuration(new MockEnvironment().withProperty("artemis.hyperion.exercise-generation.enabled", "true"));
        assertThat(config.getMemberAttributeConfig().getAttribute(DistributedDataProvider.WRITER_FENCING_MEMBER_ATTRIBUTE)).isEqualTo("true");
    }

    @Test
    void majorityRetainsTheSlotButAnUnreplicatedIslandCannotAccessIt() {
        String cluster = "generation-fencing-" + UUID.randomUUID();
        var members = new ArrayList<HazelcastInstance>();
        HazelcastInstance island = null;
        try {
            members.add(Hazelcast.newHazelcastInstance(clusterConfiguration(cluster, List.of())));
            String seed = "127.0.0.1:" + members.getFirst().getCluster().getLocalMember().getSocketAddress().getPort();
            members.add(Hazelcast.newHazelcastInstance(clusterConfiguration(cluster, List.of(seed))));
            members.add(Hazelcast.newHazelcastInstance(clusterConfiguration(cluster, List.of(seed))));
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(members.getFirst().getCluster().getMembers()).hasSize(3));
            var provider = new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService(members.getFirst());
            assertThat(provider.getCoordinationSnapshot().orElseThrow().allWritersFenced()).isTrue();
            var slots = members.getFirst().<String, String>getMap(Constants.HYPERION_GENERATION_JOBS_MAP);
            slots.put("42", "persisting");
            members.getLast().shutdown();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(members.getFirst().getCluster().getMembers()).hasSize(2));
            assertThat(slots.get("42")).isEqualTo("persisting");
            assertThat(new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService(members.getFirst()).getCoordinationSnapshot()
                    .orElseThrow().permitsAdmission(3)).isFalse();
            island = Hazelcast.newHazelcastInstance(clusterConfiguration(cluster + "-island", List.of()));
            var isolatedSlots = island.<String, String>getMap(Constants.HYPERION_GENERATION_JOBS_MAP);
            assertThatThrownBy(() -> isolatedSlots.get("42")).isInstanceOf(SplitBrainProtectionException.class);
            assertThatThrownBy(() -> isolatedSlots.put("42", "participation")).isInstanceOf(SplitBrainProtectionException.class);
        }
        finally {
            if (island != null) {
                island.shutdown();
            }
            members.forEach(member -> member.getLifecycleService().terminate());
        }
    }

    @Test
    void mixedExpectedMemberCountsRejectFencingDeclaration() {
        String cluster = "mixed-generation-membership-" + UUID.randomUUID();
        var first = Hazelcast.newHazelcastInstance(clusterConfiguration(cluster, List.of()));
        HazelcastInstance second = null;
        try {
            String seed = "127.0.0.1:" + first.getCluster().getLocalMember().getSocketAddress().getPort();
            Config config = clusterConfiguration(cluster, List.of(seed));
            config.getMemberAttributeConfig().setAttribute(DistributedDataProvider.WRITER_FENCING_MEMBERS_ATTRIBUTE, "1");
            second = Hazelcast.newHazelcastInstance(config);
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(first.getCluster().getMembers()).hasSize(2));
            var provider = new de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService(first);
            assertThat(provider.getCoordinationSnapshot().orElseThrow().allWritersFenced()).isFalse();
        }
        finally {
            if (second != null) {
                second.getLifecycleService().terminate();
            }
            first.getLifecycleService().terminate();
        }
    }

    private static Config clusterConfiguration(String name, List<String> seeds) {
        Config config = configuration(new MockEnvironment().withProperty("artemis.hyperion.generation.cluster-fencing-enabled", "true")
                .withProperty("jhipster.cache.hazelcast.expected-data-member-count", "3"));
        config.setClusterName(name);
        var network = config.getNetworkConfig();
        network.getInterfaces().setEnabled(true).setInterfaces(List.of("127.0.0.1"));
        network.getJoin().getAutoDetectionConfig().setEnabled(false);
        network.getJoin().getMulticastConfig().setEnabled(false);
        network.getJoin().getTcpIpConfig().setEnabled(true).setMembers(seeds);
        return config;
    }

    private static Config configuration(MockEnvironment environment) {
        var bean = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class), environment,
                Optional.empty());
        Config config = new Config();
        config.addMapConfig(new MapConfig("default").setBackupCount(1));
        ReflectionTestUtils.invokeMethod(bean, "configureMemberAttributes", config);
        ReflectionTestUtils.invokeMethod(bean, "configureSplitBrainProtection", config);
        return config;
    }
}
