package de.tum.cit.aet.artemis.aiworker.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerTransport;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

/** Registers the shared provider transport on eligible core nodes. */
@Lazy
@Configuration(proxyBeanMethods = false)
@Conditional(AiWorkerEnabled.class)
@EnableConfigurationProperties(AiWorkerProperties.class)
@Profile("!" + PROFILE_AIWORKER)
public class AiWorkerMessagingConfiguration {

    @Bean
    public WorkerMessageCodecApi aiWorkerMessageCodecApi() {
        return new WorkerMessageCodecApi();
    }

    @Bean
    public WorkerTransport aiWorkerTransport(DistributedDataProvider provider, WorkerMessageCodecApi codec) {
        return new WorkerTransport(provider, codec);
    }
}
