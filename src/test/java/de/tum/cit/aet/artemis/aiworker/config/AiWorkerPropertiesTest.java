package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class AiWorkerPropertiesTest {

    @Test
    void workerProfileSamplesEveryGenerationTraceByDefault() throws Exception {
        var environment = new MockEnvironment();
        for (var source : new YamlPropertySourceLoader().load("aiworker", new ClassPathResource("config/application-aiworker.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        assertThat(environment.getProperty("management.tracing.sampling.probability", Double.class)).isEqualTo(1.0);
    }

    @Test
    void rejectsDuplicateAndUnsafeIds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AiWorkerProperties(List.of("worker", "worker"), Duration.ofSeconds(30), Duration.ofSeconds(45)));
        assertThatIllegalArgumentException().isThrownBy(() -> new AiWorkerProperties(List.of("worker.unsafe"), Duration.ofSeconds(30), Duration.ofSeconds(45)));
    }

    @Test
    void rejectsInvalidTimeouts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AiWorkerProperties(List.of("worker"), Duration.ofSeconds(10), Duration.ofSeconds(45)));
        assertThatIllegalArgumentException().isThrownBy(() -> new AiWorkerProperties(List.of("worker"), Duration.ofSeconds(30), Duration.ofSeconds(30)));
    }
}
