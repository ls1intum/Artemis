package de.tum.cit.aet.artemis.aiworker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.config.EurekaInstanceHelper;
import de.tum.cit.aet.artemis.core.config.HazelcastConfiguration;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisNodeIdentity;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;

/** Loads only the shared distributed-data client and its required support on a standalone worker. */
@Configuration(proxyBeanMethods = false)
@Profile("aiworker-standalone")
@EnableConfigurationProperties({ ArtemisProperties.class, ServerProperties.class })
@Import({ EurekaInstanceHelper.class, HazelcastConfiguration.class, HazelcastDistributedDataProviderService.class, RedissonCodecConfiguration.class, RedisNodeIdentity.class,
        RedisClientListResolver.class, RedissonDistributedDataProviderService.class, LocalDataProviderService.class })
public class WorkerDistributedDataConfiguration {
}
