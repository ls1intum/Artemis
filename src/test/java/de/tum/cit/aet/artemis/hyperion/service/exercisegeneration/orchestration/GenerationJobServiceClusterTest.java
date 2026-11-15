package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFeedbackDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationInputDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Checks per-exercise admission, cancellation and replay using two real Hazelcast members. This does not qualify automatic failover. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationJobServiceClusterTest {

    private static final Duration STALE_JOB_TIMEOUT = Duration.ofMinutes(35);

    private static final Duration MAX_JOB_DURATION = Duration.ofMinutes(30);

    private HazelcastInstance firstMember;

    private HazelcastInstance secondMember;

    private GenerationJobService firstNode;

    private GenerationJobService secondNode;

    @BeforeAll
    void startCluster() {
        String clusterName = "hyperion-job-service-cluster-test-" + System.nanoTime();
        firstMember = Hazelcast.newHazelcastInstance(clusterConfig(clusterName, List.of()));
        int firstMemberPort = firstMember.getCluster().getLocalMember().getSocketAddress().getPort();
        secondMember = Hazelcast.newHazelcastInstance(clusterConfig(clusterName, List.of("127.0.0.1:" + firstMemberPort)));
        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(firstMember.getCluster().getMembers()).hasSize(2).hasSameSizeAs(secondMember.getCluster().getMembers()));
        assertThat(localMemberId(firstMember)).isNotEqualTo(localMemberId(secondMember));
    }

    /**
     * Joins over TCP/IP on the loopback interface: multicast is unavailable or noisy on CI, and auto-detection could join a stray member from another build.
     * <p>
     * Each member requests an ephemeral port so other local test processes cannot capture its loopback connections.
     * {@code seedMembers} must name the founding member's actual bound port: a bare host scans only the three ports from 5701.
     * The founding member starts with no seeds and its bound port is read back for the second.
     */
    private static Config clusterConfig(String clusterName, List<String> seedMembers) {
        Config config = new Config();
        config.setClusterName(clusterName);
        NetworkConfig network = config.getNetworkConfig();
        network.setPort(0).setPortAutoIncrement(false);
        network.getInterfaces().setEnabled(true).setInterfaces(List.of("127.0.0.1"));
        JoinConfig join = network.getJoin();
        join.getAutoDetectionConfig().setEnabled(false);
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).setMembers(seedMembers);
        return config;
    }

    @BeforeEach
    void setUp() {
        firstMember.getDistributedObjects().forEach(distributedObject -> distributedObject.destroy());
        firstNode = nodeOn(firstMember);
        secondNode = nodeOn(secondMember);
    }

    @AfterAll
    void stopCluster() {
        if (secondMember != null) {
            secondMember.shutdown();
        }
        if (firstMember != null) {
            firstMember.shutdown();
        }
    }

    private static GenerationJobService nodeOn(HazelcastInstance member) {
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(member), event -> {
        }, mock(GenerationTokenUsageService.class), null, STALE_JOB_TIMEOUT, MAX_JOB_DURATION, Runnable::run);
        service.init();
        return service;
    }

    private static String localMemberId(HazelcastInstance member) {
        return member.getCluster().getLocalMember().getUuid().toString();
    }

    private static User user(String login) {
        User user = new User();
        user.setLogin(login);
        return user;
    }

    private static ProgrammingExercise exercise(long id) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }

    @Test
    void inputAndDesignReplayAcrossNodesWithoutDisclosingOwnerFeedback() {
        var firstReplay = new GenerationJobReplayStore(HyperionDistributedDataTestProvider.provider(firstMember), Duration.ofHours(4), Duration.ofHours(4),
                HyperionDistributedDataTestProvider.provider(firstMember).getExpiringMap(GenerationJobService.CANCEL_MAP_NAME, Duration.ofHours(4)));
        var secondReplay = new GenerationJobReplayStore(HyperionDistributedDataTestProvider.provider(secondMember), Duration.ofHours(4), Duration.ofHours(4),
                HyperionDistributedDataTestProvider.provider(secondMember).getExpiringMap(GenerationJobService.CANCEL_MAP_NAME, Duration.ofHours(4)));
        var input = new ExerciseGenerationInputDTO("Keep the API.",
                List.of(new ExerciseGenerationFeedbackDTO("SOLUTION_REPO", "src/Stack.java", 12, List.of("Handle empty stacks."))));
        var exercise = new ProgrammingExercise();
        exercise.setId(810L);
        var owner = new User();
        owner.setLogin("owner");
        var otherInstructor = new User();
        otherInstructor.setLogin("other");
        String jobId = firstNode.startJob(owner, exercise, "Keep the API.", GenerationMode.ADAPT, null, null, null, input);
        assertThat(firstReplay.recordSpecDocument(810L, jobId, "# Approved stack design")).isTrue();

        assertThat(secondReplay.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.input()).isEqualTo(input);
            assertThat(status.specDocument()).isEqualTo("# Approved stack design");
        });
        assertThat(secondReplay.getStatus(otherInstructor, exercise)).hasValueSatisfying(status -> {
            assertThat(status.input()).isNull();
            assertThat(status.specDocument()).isNull();
        });

        firstNode.clearJob(810L, jobId);
        secondReplay.discardRetainedRun(810L, jobId);
        assertThat(firstReplay.getStatus(owner, exercise)).isEmpty();
    }

    @Test
    void enterNonCancellablePhase_isRefusedOnANodeThatDoesNotOwnTheJob() {
        long exerciseId = 900L;
        String jobId = firstNode.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);

        assertThat(secondNode.enterNonCancellablePhase(exerciseId, jobId)).isFalse();
        assertThat(firstNode.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
    }

    @Test
    void isOwnedActiveJob_distinguishesTheOwningNodeFromItsPeer() {
        long exerciseId = 901L;
        String jobId = firstNode.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);

        assertThat(firstNode.isOwnedActiveJob(exerciseId, jobId)).isTrue();
        assertThat(secondNode.isOwnedActiveJob(exerciseId, jobId)).isFalse();
        // Both nodes still agree the job exists; only ownership differs.
        assertThat(secondNode.isActiveJob(exerciseId, jobId)).isTrue();
    }

    @Test
    void heartbeat_isRefusedOnANodeThatDoesNotOwnTheJob() {
        long exerciseId = 902L;
        String jobId = firstNode.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);

        assertThat(secondNode.heartbeat(exerciseId, jobId)).isFalse();
        assertThat(firstNode.heartbeat(exerciseId, jobId)).isTrue();
    }

    /** The per-exercise slot is cluster-wide: a second node must not be able to start a competing run for the same exercise. */
    @Test
    void startJob_onOneNodeBlocksTheSameExerciseOnTheOther() {
        long exerciseId = 903L;
        firstNode.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);

        assertThat(secondNode.hasActiveJob(exerciseId)).isTrue();
        assertThatThrownBy(() -> secondNode.startJob(user("other"), exercise(exerciseId), "generate", GenerationMode.GENERATE)).isInstanceOf(ConflictException.class);
    }

    /** The cancel hook closes over live sandbox objects and is therefore node-local, so cancellation depends on the cluster-wide interrupt topic reaching the owner. */
    @Test
    void requestCancellation_fromAPeerNodeRunsTheOwningNodesCancelHook() {
        long exerciseId = 904L;
        User owner = user("owner");
        String jobId = firstNode.startJob(owner, exercise(exerciseId), "generate", GenerationMode.GENERATE);
        AtomicBoolean hookRan = new AtomicBoolean(false);
        firstNode.registerCancelHook(jobId, () -> hookRan.set(true));

        assertThat(secondNode.requestCancellation(exerciseId, jobId, owner)).isTrue();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(hookRan).isTrue());
        assertThat(firstNode.isCancelled(jobId)).isTrue();
    }

    @Test
    void exactTokenOperatorResetIsVisibleOnBothNodes() {
        long exerciseId = 905L;
        String jobId = firstNode.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
        assertThat(firstNode.enterNonCancellablePhase(exerciseId, jobId)).isTrue();

        assertThat(secondNode.recoverWedgedSlot(exerciseId, "wrong-token")).isFalse();
        assertThat(firstNode.hasActiveJob(exerciseId)).isTrue();
        assertThat(secondNode.recoverWedgedSlot(exerciseId, jobId)).isTrue();
        assertThat(firstNode.hasActiveJob(exerciseId)).isFalse();
        assertThat(secondNode.startJob(user("owner"), exercise(exerciseId), "retry", GenerationMode.GENERATE)).isNotBlank();
    }

}
