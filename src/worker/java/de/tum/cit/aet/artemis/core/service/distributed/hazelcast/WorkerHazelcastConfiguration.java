package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.core.config.EurekaInstanceHelper;
import de.tum.cit.aet.artemis.core.config.HazelcastDistributedDataCondition;

/** Opens a cluster client for dedicated workers that select the Hazelcast provider. */
@Lazy
@Configuration(proxyBeanMethods = false)
@Conditional(HazelcastDistributedDataCondition.class)
public class WorkerHazelcastConfiguration {

    @Bean(destroyMethod = "shutdown")
    HazelcastInstance hazelcastInstance(ApplicationContext context, Environment environment, EurekaInstanceHelper discovery,
            @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name:Artemis}") String instanceName,
            @Value("${spring.hazelcast.localInstances:true}") boolean localInstances,
            @Value("${artemis.distributed-data.additional-allowed-types:}") List<String> additionalTypes) {
        return new WorkerHazelcastClientFactory(context, discovery, environment, instanceName, localInstances, additionalTypes).createHazelcastClient();
    }
}
