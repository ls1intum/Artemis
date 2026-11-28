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
import org.redisson.api.RedissonClient;

import com.redis.testcontainers.RedisContainer;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFeedbackDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationInputDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

/** Checks per-exercise admission, cancellation and replay using independent Redis clients. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerationJobServiceClusterTest {

    private static final Duration STALE_JOB_TIMEOUT = Duration.ofMinutes(35);

    private static final Duration MAX_JOB_DURATION = Duration.ofMinutes(30);

    private final RedisContainer store = ValkeyTestContainerFactory.create();

    private RedissonClient firstClient;

    private RedissonClient secondClient;

    private DistributedDataProvider firstProvider;

    private DistributedDataProvider secondProvider;

    private GenerationJobService firstNode;

    private GenerationJobService secondNode;

    @BeforeAll
    void startCluster() {
        store.start();
        String address = "redis://" + store.getHost() + ":" + store.getMappedPort(6379);
        firstClient = GenerationRedisTestSupport.client(address, "artemis-generation-first");
        secondClient = GenerationRedisTestSupport.client(address, "artemis-generation-second");
        firstProvider = GenerationRedisTestSupport.provider(firstClient);
        secondProvider = GenerationRedisTestSupport.provider(secondClient);
    }

    @BeforeEach
    void setUp() {
        firstClient.getKeys().flushdb();
        firstNode = nodeOn(firstProvider);
        secondNode = nodeOn(secondProvider);
    }

    @AfterAll
    void stopCluster() {
        if (secondClient != null) {
            secondClient.shutdown();
        }
        if (firstClient != null) {
            firstClient.shutdown();
        }
        store.stop();
    }

    private static GenerationJobService nodeOn(DistributedDataProvider provider) {
        GenerationJobService service = new GenerationJobService(provider, event -> {
        }, mock(GenerationTokenUsageService.class), null, STALE_JOB_TIMEOUT, MAX_JOB_DURATION, Runnable::run);
        service.init();
        return service;
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
        var firstReplay = new GenerationJobReplayStore(firstProvider, Duration.ofHours(4), Duration.ofHours(4),
                firstProvider.getExpiringMap(GenerationJobService.CANCEL_MAP_NAME, Duration.ofHours(4)));
        var secondReplay = new GenerationJobReplayStore(secondProvider, Duration.ofHours(4), Duration.ofHours(4),
                secondProvider.getExpiringMap(GenerationJobService.CANCEL_MAP_NAME, Duration.ofHours(4)));
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
