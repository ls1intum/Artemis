package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.config.DisableRedisAutoConfig;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.WorkerHazelcastConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;

/** Network providers used by the two dedicated worker executables. Only core may start a Hazelcast member. */
@Lazy
@Configuration(proxyBeanMethods = false)
@Profile(PROFILE_BUILDAGENT + " | " + PROFILE_AIWORKER)
@Import({ EurekaClientConfiguration.class, EurekaInstanceHelper.class, WorkerHazelcastConfiguration.class, HazelcastDistributedDataProviderService.class,
        DisableRedisAutoConfig.class, RedissonCodecConfiguration.class, RedisClientListResolver.class, RedissonDistributedDataProviderService.class })
public class WorkerProviderConfiguration {
}
