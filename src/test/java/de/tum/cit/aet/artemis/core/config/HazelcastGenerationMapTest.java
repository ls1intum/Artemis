package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;

class HazelcastGenerationMapTest {

    @Test
    void generationJobsDoNotExpireOrRequireAnAdditionalClusterPolicy() {
        var configuration = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class),
                new MockEnvironment(), Optional.empty());
        Config config = new Config();
        config.addMapConfig(new MapConfig("default").setBackupCount(1));

        ReflectionTestUtils.invokeMethod(configuration, "configureSplitBrainProtection", config);

        var jobs = config.getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP);
        assertThat(jobs.getTimeToLiveSeconds()).isZero();
        assertThat(jobs.getMaxIdleSeconds()).isZero();
        assertThat(jobs.getEvictionConfig().getEvictionPolicy()).isEqualTo(EvictionPolicy.NONE);
        assertThat(jobs.getBackupCount()).isEqualTo(1);
        assertThat(jobs.getSplitBrainProtectionName()).isNull();
    }
}
