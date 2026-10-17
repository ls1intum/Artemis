package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerTransport;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class AiWorkerCohostConfigurationTest {

    @Test
    void coreAndWorkerShareOneProviderTransport() {
        new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("core", "aiworker"))
                .withPropertyValues("artemis.aiworker.enabled=true", "artemis.aiworker.ids[0]=worker-1", "artemis.aiworker.id=worker-1",
                        "artemis.aiworker.image=sha256:" + "a".repeat(64), "artemis.aiworker.workload=test", "artemis.aiworker.profile=sample")
                .withBean(DistributedDataProvider.class, LocalDataProviderService::new).withUserConfiguration(AiWorkerMessagingConfiguration.class, WorkerMessaging.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(WorkerMessageCodecApi.class).hasSingleBean(WorkerTransport.class).hasSingleBean(WorkerEventPublisher.class);
                });
    }
}
