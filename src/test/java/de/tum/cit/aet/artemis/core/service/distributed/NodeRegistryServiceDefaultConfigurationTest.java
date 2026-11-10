package de.tum.cit.aet.artemis.core.service.distributed;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

/**
 * Resolves the node name against the shipped application configuration, which sets a default {@code eureka.instance.instanceId}.
 */
class NodeRegistryServiceDefaultConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(DistributedDataProvider.class, LocalDataProviderService::new)
            .withUserConfiguration(NodeRegistryService.class).withInitializer(context -> {
                context.getEnvironment().setActiveProfiles(PROFILE_CORE);
                try {
                    var resource = new FileSystemResource("src/main/resources/config/application.yml");
                    for (var source : new YamlPropertySourceLoader().load("shipped-application", resource)) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                }
                catch (IOException e) {
                    throw new IllegalStateException("Cannot load the shipped application configuration", e);
                }
            });

    @Test
    void redisNodeWithoutEurekaSettingsUsesRedisClientName() {
        assertPublishedInstanceId(contextRunner.withPropertyValues("spring.data.redis.client-name=artemis-redis-node-1"), "artemis-redis-node-1");
    }

    @Test
    void explicitEurekaInstanceIdTakesPrecedence() {
        assertPublishedInstanceId(contextRunner.withPropertyValues("spring.data.redis.client-name=artemis-redis-node-1", "eureka.instance.instanceId=Artemis:2"), "Artemis:2");
    }

    @Test
    void hazelcastNodeKeepsShippedEurekaInstanceId() {
        assertPublishedInstanceId(contextRunner, "Artemis:1");
    }

    private static void assertPublishedInstanceId(ApplicationContextRunner runner, String expected) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            NodeRegistryService registry = context.getBean(NodeRegistryService.class);
            registry.heartbeat();
            assertThat(registry.getLiveNodes()).singleElement().extracting(ClusterNodeInfo::instanceId).isEqualTo(expected);
        });
    }
}
