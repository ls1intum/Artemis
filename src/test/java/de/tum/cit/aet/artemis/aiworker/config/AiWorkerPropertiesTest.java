package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiWorkerPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = { "trustAll=true", "trustAll=TRUE", "trustAll=TrUe", "verifyHost=false", "verifyHost=FALSE", "verifyHost=FaLsE" })
    void rejectsInsecureBooleanOptionsRegardlessOfCase(String option) {
        assertThatIllegalArgumentException().isThrownBy(() -> new AiWorkerProperties("tcp://broker.invalid:61617?sslEnabled=true&" + option, "core", "test-only", List.of("worker"),
                Duration.ofSeconds(30), Duration.ofSeconds(45)));
    }
}
