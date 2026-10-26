package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.BackwardCompatibleSerializationCodec;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

@EnabledIf("isDockerAvailable")
class ExerciseGenerationRevertServiceRedisTest {

    static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void completedRevertRemovesAReadableBaselineWithNoncanonicalStoredBytes() throws Exception {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var redis = client("redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379), "legacy-baseline-test");
            try {
                var provider = provider(redis, "legacy-baseline-test");
                var grading = new GenerationGrading.Snapshot(
                        Map.of("a-test", new GenerationGrading.Grading(1.0, Visibility.ALWAYS, null), "z-test", new GenerationGrading.Grading(2.0, Visibility.ALWAYS, null)));
                var baseline = new ExerciseGenerationBaseline("legacy-job", GenerationMode.GENERATE, Map.of(), Map.of(), "before", "Before", "generated", "Generated", "main",
                        grading, grading);
                var bytes = new ByteArrayOutputStream();
                // Emulate a readable older representation: deserialization canonicalizes the record's map, changing its encoded bytes.
                class LegacySnapshotStream extends ObjectOutputStream {

                    LegacySnapshotStream() throws IOException {
                        super(bytes);
                        enableReplaceObject(true);
                    }

                    @Override
                    protected Object replaceObject(Object value) {
                        if (value == grading.tests()) {
                            var oldOrder = new LinkedHashMap<String, GenerationGrading.Grading>();
                            oldOrder.put("z-test", grading.tests().get("z-test"));
                            oldOrder.put("a-test", grading.tests().get("a-test"));
                            return oldOrder;
                        }
                        return value;
                    }
                }
                try (var stream = new LegacySnapshotStream()) {
                    stream.writeObject(baseline);
                }
                var rawCodec = new CompositeCodec(new BackwardCompatibleSerializationCodec(), ByteArrayCodec.INSTANCE);
                redis.<Long, byte[]>getMapCache(DistributedDataSchema.currentKeyFor("hyperion-exercise-generation-baselines"), rawCodec).put(42L, bytes.toByteArray(), 7,
                        TimeUnit.DAYS);
                var baselines = provider.<Long, ExerciseGenerationBaseline>getExpiringMap("hyperion-exercise-generation-baselines", Duration.ofDays(7));
                var decoded = baselines.get(42L);
                assertThat(decoded).isEqualTo(baseline);
                assertThat(decoded.previousGrading().tests().keySet()).containsExactly("a-test", "z-test");
                assertThat(baselines.remove(42L, decoded)).as("legacy bytes cannot be removed by re-encoding the decoded baseline").isFalse();

                var repositories = mock(GenerationRepositoryRevertService.class);
                var persistence = mock(GenerationPersistenceService.class);
                when(persistence.canRestoreGrading(anyLong(), any(), any())).thenReturn(true);
                when(persistence.canRestoreProblemStatementAndTitle(any(), any(), any(), any(), any())).thenReturn(true);
                when(persistence.resyncAfterRevertWithSignal(any(), any(), any(), any(), any(), any(), any(), anyMap(), any(), any(), any())).thenReturn(true);
                when(repositories.revert(any(), any(), eq("main"), any())).thenReturn(new GenerationRepositoryRevertService.RepositoryRevertResult(true, List.of(), true));
                when(repositories.captureRepositoryHeads(any(), eq("main"), any())).thenReturn(Map.of());
                var service = new ExerciseGenerationRevertService(provider, repositories, persistence, "main", new GenerationLiveSaveService(provider, true));
                service.init();
                var exercise = exercise();
                exercise.setTitle("Generated");
                exercise.setProblemStatement("generated");

                assertThat(service.revert(exercise, owner(), () -> true)).hasValueSatisfying(result -> assertThat(result.fullyReverted()).isTrue());

                assertThat(service.findRevertibleJobId(42L)).isEmpty();
                assertThat(service.revert(exercise, owner(), () -> true)).isEmpty();
            }
            finally {
                redis.shutdown();
            }
        }
    }

    private static RedissonDistributedDataProviderService provider(RedissonClient redis, String name) {
        var provider = new RedissonDistributedDataProviderService(redis, new RedisClientListResolver(new RedissonConnectionFactory(redis)));
        ReflectionTestUtils.setField(provider, "redisClientName", name);
        return provider;
    }

    private static RedissonClient client(String address, String name) {
        var config = new Config();
        config.useSingleServer().setAddress(address).setClientName(name).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer().customize(config);
        return Redisson.create(config);
    }

    private static User owner() {
        var user = new User();
        user.setLogin("editor");
        return user;
    }

    private static ProgrammingExercise exercise() {
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        return exercise;
    }
}
