package de.tum.cit.aet.artemis.core.service.distributed;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

import com.redis.testcontainers.RedisContainer;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

@SpringBootTest
@ActiveProfiles({ PROFILE_BUILDAGENT, PROFILE_TEST_BUILDAGENT })
@TestPropertySource(properties = { "artemis.continuous-integration.data-store=Redis", "spring.data.redis.client-name=artemis-node-1" })
// requires docker for testContainers to start test redis instance
@EnabledIf("isDockerAvailable")
class RedissonDistributedDataTest extends AbstractDistributedDataTest {

    @Autowired
    protected RedissonDistributedDataProviderService redissonDistributedDataProvider;

    private static RedisContainer valkey;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void beforeAll() {
        valkey = ValkeyTestContainerFactory.create();
        valkey.start();
        System.setProperty("spring.data.redis.host", valkey.getHost());
        System.setProperty("spring.data.redis.port", valkey.getMappedPort(6379).toString());
    }

    @AfterAll
    static void afterAll() {
        // JUnit runs @AfterAll even when @BeforeAll threw, and create() throws when the version property is missing.
        // Without the guard that failure would surface as a NullPointerException here instead.
        if (valkey != null) {
            valkey.stop();
        }
    }

    @Override
    protected boolean clientsReachCoreNodesDirectly() {
        // Clients connect to Redis, which is not a core node and not on the path a clone takes
        return false;
    }

    @Override
    protected DistributedDataProvider getDistributedDataProvider() {
        return redissonDistributedDataProvider;
    }
}
