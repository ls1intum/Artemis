package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SplitBrainProtectionConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.splitbrainprotection.SplitBrainProtectionOn;

import de.tum.cit.aet.artemis.core.config.HazelcastConfiguration;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;

class ProgrammingExerciseMutationGuardHazelcastTest {

    private static final String SPLIT_BRAIN_PROTECTION_NAME = "artemis-split-brain-protection";

    @Test
    @Timeout(60)
    void disabledGuardRequiresACompleteExplicitlyAllFalseRealMemberTopology() throws IOException {
        String clusterName = "mutation-guard-real-members-" + UUID.randomUUID();
        int firstPort = findAvailablePort();
        int secondPort = findAvailablePort();
        while (secondPort == firstPort) {
            secondPort = findAvailablePort();
        }
        List<String> memberAddresses = List.of("127.0.0.1:" + firstPort, "127.0.0.1:" + secondPort);
        List<HazelcastInstance> instances = new ArrayList<>();

        try {
            HazelcastInstance disabledMember = startMember(clusterName, firstPort, memberAddresses, "false");
            instances.add(disabledMember);
            ProgrammingExerciseMutationGuard guard = new ProgrammingExerciseMutationGuard(Optional.empty(), disabledMember, 2);

            assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("hyperionDataMemberTopologyMismatch"));

            HazelcastInstance capableMember = startMember(clusterName, secondPort, memberAddresses, "true");
            instances.add(capableMember);
            awaitMembershipSize(disabledMember, 2);
            awaitMembershipSize(capableMember, 2);
            assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("hyperionExerciseGenerationProfileSkew"));

            capableMember.shutdown();
            awaitMembershipSize(disabledMember, 1);
            HazelcastInstance disabledReplacement = startMember(clusterName, secondPort, memberAddresses, "false");
            instances.add(disabledReplacement);
            awaitMembershipSize(disabledMember, 2);
            awaitMembershipSize(disabledReplacement, 2);
            assertThatCode(() -> guard.claimExternalMutation(42L).close()).doesNotThrowAnyException();

            disabledReplacement.shutdown();
            awaitMembershipSize(disabledMember, 1);
            HazelcastInstance unknownReplacement = startMember(clusterName, secondPort, memberAddresses, null);
            instances.add(unknownReplacement);
            awaitMembershipSize(disabledMember, 2);
            awaitMembershipSize(unknownReplacement, 2);
            assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("hyperionExerciseGenerationCapabilityUnavailable"));
        }
        finally {
            instances.reversed().stream().filter(instance -> instance.getLifecycleService().isRunning()).forEach(HazelcastInstance::shutdown);
        }
    }

    private static HazelcastInstance startMember(String clusterName, int port, List<String> memberAddresses, String capability) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.getNetworkConfig().setPort(port).setPortAutoIncrement(false);
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        var joinConfig = config.getNetworkConfig().getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getAutoDetectionConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(true).setMembers(memberAddresses);
        if (capability != null) {
            config.getMemberAttributeConfig().setAttribute(HazelcastConfiguration.HYPERION_EXERCISE_GENERATION_CAPABLE_MEMBER_ATTRIBUTE, capability);
        }
        config.addSplitBrainProtectionConfig(
                new SplitBrainProtectionConfig().setName(SPLIT_BRAIN_PROTECTION_NAME).setEnabled(true).setMinimumClusterSize(2).setProtectOn(SplitBrainProtectionOn.READ_WRITE));
        config.addMapConfig(new MapConfig("hyperion-exercise-generation-jobs").setBackupCount(1).setSplitBrainProtectionName(SPLIT_BRAIN_PROTECTION_NAME));
        return Hazelcast.newHazelcastInstance(config);
    }

    private static void awaitMembershipSize(HazelcastInstance instance, int expectedSize) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(instance.getCluster().getMembers()).hasSize(expectedSize));
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setReuseAddress(true);
            return serverSocket.getLocalPort();
        }
    }
}
