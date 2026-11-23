package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.core.config.llm.LlmConfiguration;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * An AI worker context with the real LLM catalog, client factory and Java Gradle adapter, and one preset {@code testpreset} on the endpoint {@code test}.
 */
final class WorkerLlmTestContext {

    private WorkerLlmTestContext() {
    }

    static ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker"))
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(LlmConfiguration.class, LlmClientService.class, JavaGradleGenerationAdapterService.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new).withPropertyValues("artemis.aiworker.workload=hyperion-generation",
                        "artemis.llm.endpoints.test.base-url=https://example.invalid/v1", "artemis.llm.endpoints.test.api-key=test-key",
                        "artemis.llm.presets.testpreset.endpoint=test", "artemis.llm.presets.testpreset.model=test-model", "artemis.llm.presets.testpreset.api=chat-completions");
    }
}
