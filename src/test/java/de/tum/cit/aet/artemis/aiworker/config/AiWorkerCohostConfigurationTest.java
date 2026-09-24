package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;

class AiWorkerCohostConfigurationTest {

    @Test
    void coreAndWorkerUseSeparateBrokerConnectionsAndOneCodec() {
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("core", "aiworker"))
                .withPropertyValues("artemis.aiworker.enabled=true", "artemis.aiworker.broker-url=tcp://broker:61617?sslEnabled=true&verifyHost=true", "artemis.aiworker.user=core",
                        "artemis.aiworker.password=secret", "artemis.aiworker.ids[0]=worker-1", "artemis.aiworker.id=worker-1", "artemis.aiworker.image=sha256:" + "a".repeat(64),
                        "artemis.aiworker.workload=test", "artemis.aiworker.profile=sample", "spring.artemis.broker-url=tcp://broker:61617?sslEnabled=true&verifyHost=true",
                        "spring.artemis.user=worker", "spring.artemis.password=secret")
                .withUserConfiguration(AiWorkerMessagingConfiguration.class, WorkerBrokerConfiguration.class, WorkerMessaging.class).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerMessageCodecApi.class).hasSingleBean(DefaultJmsListenerContainerFactory.class)
                            .hasSingleBean(WorkerEventPublisher.class);
                    assertThat(context.getBean("workerConnectionFactory")).isNotSameAs(context.getBean("aiWorkerConnectionFactory"));
                });
    }
}
