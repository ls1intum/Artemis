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

    /**
     * Each scenario below only depends on the cluster membership observed at the moment {@link ProgrammingExerciseMutationGuard#claimExternalMutation(long)} runs
     * (the guard re-reads {@code hazelcastInstance.getCluster().getMembers()} on every call), so every scenario stands up exactly the real Hazelcast topology it needs
     * from scratch instead of replaying a shared multi-step transition through a single test.
     */
    @Test
    @Timeout(60)
    void incompleteTopologyRejectsWithDataMemberTopologyMismatch() throws IOException {
        String clusterName = "mutation-guard-incomplete-" + UUID.randomUUID();
        List<HazelcastInstance> instances = new ArrayList<>();
        try {
            int port = findAvailablePort();
            HazelcastInstance soleMember = startMember(clusterName, port, List.of("127.0.0.1:" + port), "false");
            instances.add(soleMember);
            ProgrammingExerciseMutationGuard guard = new ProgrammingExerciseMutationGuard(Optional.empty(), soleMember, 2);

            assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("hyperionDataMemberTopologyMismatch"));
        }
        finally {
            shutdownAll(instances);
        }
    }

    @Test
    @Timeout(60)
    void completeTopologyWithACapableMemberRejectsWithProfileSkew() throws IOException {
        String clusterName = "mutation-guard-profile-skew-" + UUID.randomUUID();
        List<HazelcastInstance> instances = new ArrayList<>();
        try {
            HazelcastInstance disabledMember = joinTwoMemberCluster(clusterName, instances, "false", "true");
            ProgrammingExerciseMutationGuard guard = new ProgrammingExerciseMutationGuard(Optional.empty(), disabledMember, 2);

            assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("hyperionExerciseGenerationProfileSkew"));
        }
        finally {
            shutdownAll(instances);
        }
    }

    @Test
    @Timeout(60)
    void completeTopologyWithAllMembersDisabledAllowsTheNoOpLease() throws IOException {
        String clusterName = "mutation-guard-all-disabled-" + UUID.randomUUID();
        List<HazelcastInstance> instances = new ArrayList<>();
        try {
            HazelcastInstance disabledMember = joinTwoMemberCluster(clusterName, instances, "false", "false");
            ProgrammingExerciseMutationGuard guard = new ProgrammingExerciseMutationGuard(Optional.empty(), disabledMember, 2);

            assertThatCode(() -> guard.claimExternalMutation(42L).close()).doesNotThrowAnyException();
        }
        finally {
            shutdownAll(instances);
        }
    }

    @Test
    @Timeout(60)
    void completeTopologyWithAnUnknownCapabilityAttributeRejectsAsUnavailable() throws IOException {
        String clusterName = "mutation-guard-unknown-capability-" + UUID.randomUUID();
        List<HazelcastInstance> instances = new ArrayList<>();
        try {
            HazelcastInstance disabledMember = joinTwoMemberCluster(clusterName, instances, "false", null);
            ProgrammingExerciseMutationGuard guard = new ProgrammingExerciseMutationGuard(Optional.empty(), disabledMember, 2);

            assertThatExceptionOfType(ServiceUnavailableAlertException.class).isThrownBy(() -> guard.claimExternalMutation(42L))
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("hyperionExerciseGenerationCapabilityUnavailable"));
        }
        finally {
            shutdownAll(instances);
        }
    }

    /** Starts two real Hazelcast members with the given capability attributes, adds them to {@code instances}, and returns the first once both have joined. */
    private static HazelcastInstance joinTwoMemberCluster(String clusterName, List<HazelcastInstance> instances, String firstCapability, String secondCapability)
            throws IOException {
        int firstPort = findAvailablePort();
        int secondPort = findAvailablePort();
        while (secondPort == firstPort) {
            secondPort = findAvailablePort();
        }
        List<String> memberAddresses = List.of("127.0.0.1:" + firstPort, "127.0.0.1:" + secondPort);

        HazelcastInstance firstMember = startMember(clusterName, firstPort, memberAddresses, firstCapability);
        instances.add(firstMember);
        HazelcastInstance secondMember = startMember(clusterName, secondPort, memberAddresses, secondCapability);
        instances.add(secondMember);
        awaitMembershipSize(firstMember, 2);
        awaitMembershipSize(secondMember, 2);
        return firstMember;
    }

    private static void shutdownAll(List<HazelcastInstance> instances) {
        instances.reversed().stream().filter(instance -> instance.getLifecycleService().isRunning()).forEach(HazelcastInstance::shutdown);
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
