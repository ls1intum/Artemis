package de.tum.cit.aet.artemis.aiworker.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerTransport;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

/** Worker-side provider transport; the sandbox never receives provider credentials. */
@Configuration(proxyBeanMethods = false)
@Profile(PROFILE_AIWORKER)
@Lazy
@EnableConfigurationProperties(WorkerSettings.class)
@EnableScheduling
public class WorkerMessaging {

    @Bean
    public WorkerMessageCodecApi workerMessageCodec() {
        return new WorkerMessageCodecApi();
    }

    @Bean
    public WorkerTransport workerTransport(DistributedDataProvider provider, WorkerMessageCodecApi codec) {
        return new WorkerTransport(provider, codec);
    }

    @Bean
    public WorkerEventPublisher workerEventPublisher(WorkerTransport transport) {
        return transport::publish;
    }
}
