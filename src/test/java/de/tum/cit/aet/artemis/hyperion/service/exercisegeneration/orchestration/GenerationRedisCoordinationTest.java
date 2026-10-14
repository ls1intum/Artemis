package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisNodeIdentity;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

@EnabledIf("isDockerAvailable")
class GenerationRedisCoordinationTest {

    static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void cancellationRejectsLateFileUpdatesThroughTheExpiringRedisMap() {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var identity = new RedisNodeIdentity("artemis-core");
            var redis = client("redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379), identity);
            try {
                var resolver = new RedisClientListResolver(new RedissonConnectionFactory(redis));
                var provider = new RedissonDistributedDataProviderService(redis, resolver, identity);
                var cancellations = provider.<String, Boolean>getExpiringMap(GenerationJobService.CANCEL_MAP_NAME, Duration.ofHours(1));
                var replay = new GenerationJobReplayStore(provider, Duration.ofHours(4), Duration.ofHours(4), cancellations);
                String jobId = "cancelled-run";
                Instant now = Instant.now();
                provider.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME).put("42",
                        new GenerationJobService.JobInfo(jobId, "owner", 42, now, null, identity.connectionName(), now, true, null));
                replay.initializeStart(42, jobId, "owner", GenerationMode.GENERATE, null);
                var first = ExerciseGenerationFileChangeDTO.of("solution/Stack.java", "write", 1);
                assertThat(replay.recordFileChange(42, jobId, first)).isTrue();
                cancellations.put(jobId, true);
                var late = ExerciseGenerationFileChangeDTO.of("template/Stack.java", "write", 2);
                assertThat(replay.recordFileChange(42, jobId, late)).isFalse();
                var index = provider.<String, GenerationJobService.JobFileChangeIndex>getExpiringMap(GenerationJobReplayStore.FILE_CHANGE_MAP_NAME, Duration.ofHours(4)).get("42");
                assertThat(index.changes()).containsExactly(first);
            }
            finally {
                redis.shutdown();
            }
        }
    }

    private static RedissonClient client(String address, RedisNodeIdentity identity) {
        var config = new Config();
        config.useSingleServer().setAddress(address).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer(identity).customize(config);
        return Redisson.create(config);
    }
}
