package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisNodeIdentity;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.hyperion.test_repository.AuthoringRunTestRepository;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

@EnabledIf("isDockerAvailable")
class GenerationRedisCoordinationTest {

    static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void independentIncarnationsFenceWritersAndRetainDepartedOwnersUntilExactRecovery() throws Exception {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var firstIdentity = new RedisNodeIdentity("artemis-core");
            var secondIdentity = new RedisNodeIdentity("artemis-core");
            RedissonClient firstClient = null;
            RedissonClient secondClient = null;
            try {
                String address = "redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379);
                firstClient = client(address, firstIdentity);
                secondClient = client(address, secondIdentity);
                var resolver = new RedisClientListResolver(new RedissonConnectionFactory(secondClient));
                var firstProvider = new RedissonDistributedDataProviderService(firstClient, resolver, firstIdentity);
                var secondProvider = new RedissonDistributedDataProviderService(secondClient, resolver, secondIdentity);
                var first = new GenerationExternalMutationService(firstProvider, 1);
                var second = new GenerationExternalMutationService(secondProvider, 1);
                assertThatThrownBy(() -> first.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
                assertThatThrownBy(() -> second.claimParticipationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
                new GenerationRecoveryBootstrapService(mock(AuthoringRunTestRepository.class), firstProvider).initialize();

                assertThat(firstProvider.getLocalNodeId()).isNotEqualTo(secondProvider.getLocalNodeId());
                assertThat(firstProvider.getCoordinationSnapshot().orElseThrow().ownerNodeIds()).containsExactlyInAnyOrder(firstIdentity.connectionName(),
                        secondIdentity.connectionName());
                assertThat(resolver.getUniqueClients()).containsExactly("artemis-core");
                assertThat(resolver.getClientAddressesByName().orElseThrow().get("artemis-core")).isNotEmpty();

                var start = new CountDownLatch(1);
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var firstClaim = executor.submit(() -> claimAfter(start, first));
                    var secondClaim = executor.submit(() -> claimAfter(start, second));
                    start.countDown();
                    String firstToken = firstClaim.get(10, TimeUnit.SECONDS);
                    String secondToken = secondClaim.get(10, TimeUnit.SECONDS);
                    assertThat(firstToken == null ^ secondToken == null).isTrue();
                    if (firstToken != null) {
                        first.clearExternalMutationSlot(42, firstToken);
                    }
                    else {
                        second.clearExternalMutationSlot(42, secondToken);
                    }
                }

                String departedToken = first.claimExternalMutationSlot(42);
                assertThat(second.recoverWedgedSlot(42, departedToken)).isFalse();
                firstClient.shutdown();
                await().atMost(Duration.ofSeconds(10))
                        .untilAsserted(() -> assertThat(secondProvider.getCoordinationSnapshot().orElseThrow().ownerNodeIds()).containsExactly(secondIdentity.connectionName()));
                assertThat(second.getWedgedSlotInfo(42).orElseThrow().ownerLeftCluster()).isTrue();
                assertThatThrownBy(() -> second.claimExternalMutationSlot(42)).isInstanceOf(ConflictException.class);
                assertThat(second.recoverWedgedSlot(42, "wrong-token")).isFalse();
                assertThat(second.recoverWedgedSlot(42, departedToken)).isTrue();
                String replacementToken = second.claimExternalMutationSlot(42);
                second.clearExternalMutationSlot(42, departedToken);
                assertThat(second.isGenerationActive(42)).isTrue();
                assertThatThrownBy(() -> second.claimExternalMutationSlot(42)).isInstanceOf(ConflictException.class);
                second.clearExternalMutationSlot(42, replacementToken);
                assertThat(second.isGenerationActive(42)).isFalse();
            }
            finally {
                if (firstClient != null) {
                    firstClient.shutdown();
                }
                if (secondClient != null) {
                    secondClient.shutdown();
                }
            }
        }
    }

    private static String claimAfter(CountDownLatch start, GenerationExternalMutationService service) throws InterruptedException {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Concurrent claims were not released");
        }
        try {
            return service.claimExternalMutationSlot(42);
        }
        catch (ConflictException expected) {
            return null;
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
