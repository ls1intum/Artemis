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
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * The cross-node behaviour of {@link GenerationJobService} against two <em>real</em> Hazelcast members.
 * <p>
 * The ownership fences in {@code enterNonCancellablePhase}, {@code isOwnedActiveJob} and {@code heartbeat} compare {@code job.ownerNodeId()} against {@code localNodeId}, which
 * are equal by construction on the single member the other tests in this package share. This class forms a two-member cluster on the loopback interface so those comparisons run
 * against two distinct member UUIDs and a member can genuinely leave.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationJobServiceClusterTest {

    /** Both members must agree on the expected data-member count, which is what admission verifies before every claim. */
    private static final int EXPECTED_DATA_MEMBERS = 2;

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
     * {@code seedMembers} must name the founding member's <em>actual</em> port. A bare host scans only the three ports from 5701, which the Spring contexts running in this JVM
     * have usually taken by the time this test starts; both members then bind above that range and each forms its own single-member cluster. The founding member is started
     * with no seeds and its bound port is read back for the second.
     */
    private static Config clusterConfig(String clusterName, List<String> seedMembers) {
        Config config = new Config();
        config.setClusterName(clusterName);
        NetworkConfig network = config.getNetworkConfig();
        network.setPortAutoIncrement(true);
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
        }, mock(LLMTokenUsageService.class), null, STALE_JOB_TIMEOUT, MAX_JOB_DURATION, Runnable::run, EXPECTED_DATA_MEMBERS);
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

    /** The stale-writer fence: a node that does not own the job must not declare the point of no return and start writing to Git and the database. */
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

    /** A slot wedged in the non-cancellable phase stays claimed while its owner is alive and becomes recoverable from the surviving node only once the owner has left. */
    @Test
    void recoverWedgedSlot_becomesPossibleFromTheSurvivingNodeOnlyAfterTheOwnerLeavesTheCluster() {
        long exerciseId = 905L;
        HazelcastInstance doomedMember = Hazelcast.newHazelcastInstance(
                clusterConfig(firstMember.getConfig().getClusterName(), List.of("127.0.0.1:" + firstMember.getCluster().getLocalMember().getSocketAddress().getPort())));
        try {
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(firstMember.getCluster().getMembers()).hasSize(3));
            // Three members now, so both services must expect three or admission fails closed on the topology check.
            GenerationJobService doomedNode = clusterNodeExpecting(doomedMember, 3);
            GenerationJobService survivingNode = clusterNodeExpecting(firstMember, 3);
            String jobId = doomedNode.startJob(user("owner"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
            assertThat(doomedNode.enterNonCancellablePhase(exerciseId, jobId)).isTrue();

            assertThat(survivingNode.recoverWedgedSlot(exerciseId, jobId)).isFalse();
            assertThat(survivingNode.getWedgedSlotInfo(exerciseId)).hasValueSatisfying(info -> assertThat(info.ownerLeftCluster()).isFalse());

            doomedMember.getLifecycleService().terminate();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(firstMember.getCluster().getMembers()).hasSize(2));
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(survivingNode.getWedgedSlotInfo(exerciseId)).hasValueSatisfying(info -> assertThat(info.ownerLeftCluster()).isTrue()));

            // Two of an expected three is a majority, so recovery proceeds without waiting for the dead node to come back.
            assertThat(survivingNode.recoverWedgedSlot(exerciseId, jobId)).isTrue();
            assertThat(survivingNode.hasActiveJob(exerciseId)).isFalse();
        }
        finally {
            if (doomedMember.getLifecycleService().isRunning()) {
                doomedMember.getLifecycleService().terminate();
            }
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(firstMember.getCluster().getMembers()).hasSize(2));
        }
    }

    private static GenerationJobService clusterNodeExpecting(HazelcastInstance member, int expectedDataMembers) {
        GenerationJobService service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(member), event -> {
        }, mock(LLMTokenUsageService.class), null, STALE_JOB_TIMEOUT, MAX_JOB_DURATION, Runnable::run, expectedDataMembers);
        service.init();
        return service;
    }
}
