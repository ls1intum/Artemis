package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.BackwardCompatibleSerializationCodec;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

class WorkerDistributedDataConfigurationTest {

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
                        var provider = context.getBean(DistributedDataProvider.class);
                        assertThat(provider.getLocalMemberAddress()).startsWith("artemis-");
                        assertThat(provider.getClusterMemberAddresses()).contains(provider.getLocalMemberAddress());
                        provider.<String, String>getMap("worker-bootstrap-redis").put("ready", "yes");
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
            assertThat(context).doesNotHaveBean("redissonClient");
        });
    }

    private static ApplicationContextRunner workerContextRunner() {
        return new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker"))
                .withInitializer(new ConfigDataApplicationContextInitializer()).withUserConfiguration(WorkerDistributedDataConfiguration.class, ProviderAutoConfiguration.class)
                .withPropertyValues("artemis.aiworker.id=provider-test", "artemis.aiworker.workload=provider-test");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ProviderAutoConfiguration {
    }
}
