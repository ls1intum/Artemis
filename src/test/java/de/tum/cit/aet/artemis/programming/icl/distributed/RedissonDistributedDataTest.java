package de.tum.cit.aet.artemis.programming.icl.distributed;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

import com.redis.testcontainers.RedisStackContainer;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson.RedissonDistributedDataProviderService;

@SpringBootTest
@ActiveProfiles({ PROFILE_BUILDAGENT })
@TestPropertySource(properties = { "artemis.continuous-integration.data-store=Redis", "spring.data.redis.client-name=artemis-node-1" })
// requires docker for testContainers to start test redis instance
@EnabledIf("isDockerAvailable")
class RedissonDistributedDataTest extends AbstractDistributedDataTest {

    @Autowired
    protected RedissonDistributedDataProviderService redissonDistributedDataProvider;

    private static RedisStackContainer redis;

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
        redis = new RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));
        redis.start();
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
    }

    @AfterAll
    static void afterAll() {
        redis.stop();
    }

    @Override
    protected DistributedDataProvider getDistributedDataProvider() {
        return redissonDistributedDataProvider;
    }
}
