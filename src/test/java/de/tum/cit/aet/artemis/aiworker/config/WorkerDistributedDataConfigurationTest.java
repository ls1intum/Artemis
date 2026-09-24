package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.testcontainers.DockerClientFactory;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

class WorkerDistributedDataConfigurationTest {

    @Test
    void standaloneWorkerConnectsAsHazelcastClientToCoreMember() {
        var config = new Config();
        config.setClusterName("prod");
        config.getNetworkConfig().setPort(0).setPortAutoIncrement(false).getJoin().getMulticastConfig().setEnabled(false);
        var member = Hazelcast.newHazelcastInstance(config);
        try {
            int port = member.getCluster().getLocalMember().getSocketAddress().getPort();
            new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker", "aiworker-standalone"))
                    .withPropertyValues("artemis.distributed-data.provider=hazelcast", "spring.hazelcast.localInstances=false",
                            "artemis.aiworker.hazelcast-addresses=127.0.0.1:" + port)
                    .withBean(DiscoveryClient.class, () -> mock(DiscoveryClient.class)).withUserConfiguration(WorkerDistributedDataConfiguration.class).run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(DistributedDataProvider.class).hasSingleBean(HazelcastDistributedDataProviderService.class);
                        await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() -> {
                            context.getBean(DistributedDataProvider.class).<String, String>getMap("worker-bootstrap-probe").put("ready", "yes");
                            assertThat(member.getMap("worker-bootstrap-probe").get("ready")).isEqualTo("yes");
                        });
                    });
        }
        finally {
            member.shutdown();
        }
    }

    @Test
    @EnabledIf("dockerAvailable")
    void standaloneWorkerUsesRedisProvider() {
        try (var store = ValkeyTestContainerFactory.create()) {
            store.start();
            new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker", "aiworker-standalone"))
                    .withPropertyValues("artemis.distributed-data.provider=redis", "spring.data.redis.host=" + store.getHost(),
                            "spring.data.redis.port=" + store.getMappedPort(6379))
                    .withUserConfiguration(WorkerDistributedDataConfiguration.class).run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(DistributedDataProvider.class).hasSingleBean(RedissonDistributedDataProviderService.class);
                        context.getBean(DistributedDataProvider.class).<String, String>getMap("worker-bootstrap-redis").put("ready", "yes");
                        assertThat(context.getBean(DistributedDataProvider.class).<String, String>getMap("worker-bootstrap-redis").get("ready")).isEqualTo("yes");
                    });
        }
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void standaloneWorkerLoadsOnlySelectedLocalProvider() {
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker", "aiworker-standalone"))
                .withPropertyValues("artemis.distributed-data.provider=local").withUserConfiguration(WorkerDistributedDataConfiguration.class).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DistributedDataProvider.class).hasSingleBean(LocalDataProviderService.class);
                    assertThat(context).doesNotHaveBean("hazelcastInstance");
                    assertThat(context).doesNotHaveBean("redissonClient");
                });
    }
}
