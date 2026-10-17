package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

class AiWorkerPropertiesTest {

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
