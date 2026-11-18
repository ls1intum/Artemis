package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.http.RestClientTransportClientFactories;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.testcontainers.DockerClientFactory;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.RoutingMode;
import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.BackwardCompatibleSerializationCodec;
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
            var independentClientConfig = new ClientConfig().setClusterName("prod");
            independentClientConfig.getNetworkConfig().addAddress("127.0.0.1:" + port);
            independentClientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(20_000);
            var independentClient = HazelcastClient.newHazelcastClient(independentClientConfig);
            var workerClient = new AtomicReference<HazelcastClientProxy>();
            try {
                DiscoveryClient discovery = mock(DiscoveryClient.class);
                ServiceInstance core = mock(ServiceInstance.class);
                when(core.getMetadata()).thenReturn(Map.of("profile", "core", "hazelcast.host", "127.0.0.1", "hazelcast.port", Integer.toString(port)));
                when(core.getHost()).thenReturn("127.0.0.1");
                when(core.getPort()).thenReturn(8080);
                when(discovery.getInstances("Artemis")).thenReturn(List.of(core));
                workerContextRunner().withPropertyValues("artemis.distributed-data.provider=hazelcast", "spring.hazelcast.localInstances=false",
                        "artemis.continuous-integration.build-agent.short-name=unrelated-build-agent").withBean(DiscoveryClient.class, () -> discovery).run(context -> {
                            assertThat(context).hasNotFailed().hasSingleBean(DistributedDataProvider.class).hasSingleBean(HazelcastDistributedDataProviderService.class);
                            assertThat(context).hasSingleBean(RestClientTransportClientFactories.class);
                            assertThat(context).doesNotHaveBean(DataRedisAutoConfiguration.class).doesNotHaveBean(DataRedisReactiveAutoConfiguration.class);
                            assertThat(context).doesNotHaveBean(RedisConnectionFactory.class).doesNotHaveBean(ReactiveRedisConnectionFactory.class);
                            var client = (HazelcastClientProxy) context.getBean("hazelcastInstance");
                            workerClient.set(client);
                            assertThat(client.getName()).isEqualTo("aiworker-provider-test");
                            assertThat(client.getClientConfig().getNetworkConfig().getClusterRoutingConfig().getRoutingMode()).isEqualTo(RoutingMode.ALL_MEMBERS);
                            await().atMost(Duration.ofSeconds(20)).ignoreExceptions().untilAsserted(() -> {
                                context.getBean(DistributedDataProvider.class).<String, String>getMap("worker-bootstrap-probe").put("ready", "yes");
                                assertThat(member.getMap("worker-bootstrap-probe").get("ready")).isEqualTo("yes");
                            });
                        });
                assertThat(workerClient.get().getLifecycleService().isRunning()).isFalse();
                assertThat(member.getLifecycleService().isRunning()).isTrue();
                assertThat(independentClient.getLifecycleService().isRunning()).isTrue();
                independentClient.<String, String>getMap("worker-bootstrap-probe").put("after-worker-shutdown", "independent-client");
                assertThat(member.<String, String>getMap("worker-bootstrap-probe").get("after-worker-shutdown")).isEqualTo("independent-client");
            }
            finally {
                try {
                    if (workerClient.get() != null) {
                        workerClient.get().shutdown();
                    }
                }
                finally {
                    independentClient.shutdown();
                }
            }
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
            workerContextRunner().withPropertyValues("artemis.distributed-data.provider=redis", "spring.data.redis.host=" + store.getHost(),
                    "spring.data.redis.port=" + store.getMappedPort(6379)).run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(DistributedDataProvider.class).hasSingleBean(RedissonDistributedDataProviderService.class)
                                .hasSingleBean(RedissonAutoConfigurationV4.class).hasSingleBean(RedissonClient.class);
                        assertThat(ConditionEvaluationReport.get(context.getBeanFactory()).getExclusions()).doesNotContain(RedissonAutoConfigurationV4.class.getName());
                        assertThat(context.getBean(RedissonClient.class).getConfig().getCodec()).isInstanceOf(BackwardCompatibleSerializationCodec.class);
                        context.getBean(DistributedDataProvider.class).<String, String>getMap("worker-bootstrap-redis").put("ready", "yes");
                        assertThat(context.getBean(DistributedDataProvider.class).<String, String>getMap("worker-bootstrap-redis").get("ready")).isEqualTo("yes");
                    });
        }
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void standaloneWorkerDoesNotLoadProcessLocalProvider() {
        workerContextRunner().withPropertyValues("artemis.distributed-data.provider=local").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DistributedDataProvider.class);
            assertThat(context).doesNotHaveBean("hazelcastInstance");
            assertThat(context).doesNotHaveBean("redissonClient");
        });
    }

    private static ApplicationContextRunner workerContextRunner() {
        return new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker"))
                .withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(WorkerDistributedDataConfiguration.class, ProviderAutoConfiguration.class)
                .withPropertyValues("artemis.aiworker.id=provider-test", "artemis.aiworker.workload=provider-test", "eureka.client.enabled=false");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ProviderAutoConfiguration {
    }
}
