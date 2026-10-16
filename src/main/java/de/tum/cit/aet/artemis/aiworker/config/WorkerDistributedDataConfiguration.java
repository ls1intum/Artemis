package de.tum.cit.aet.artemis.aiworker.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.config.DisableRedisAutoConfig;
import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.config.EurekaClientConfiguration;
import de.tum.cit.aet.artemis.core.config.EurekaInstanceHelper;
import de.tum.cit.aet.artemis.core.config.HazelcastConfiguration;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;

/** Loads only the shared distributed-data client and its required support on a worker-only node. */
@Configuration(proxyBeanMethods = false)
@Lazy
@Profile(PROFILE_AIWORKER + " & !" + PROFILE_CORE)
@EnableConfigurationProperties({ ArtemisProperties.class, ServerProperties.class })
@Import({ EurekaClientConfiguration.class, EurekaInstanceHelper.class, HazelcastConfiguration.class, HazelcastDistributedDataProviderService.class, DisableRedisAutoConfig.class,
        RedissonCodecConfiguration.class, RedisClientListResolver.class, RedissonDistributedDataProviderService.class })
public class WorkerDistributedDataConfiguration {
}
