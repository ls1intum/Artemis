package de.tum.cit.aet.artemis.hyperionworker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HyperionWorkerApplicationTest {

    @ParameterizedTest
    @ValueSource(strings = { "servlet", "reactive" })
    void rejectsAttemptsToEnableAnIncomingWebServer(String type) {
        assertThatThrownBy(() -> HyperionWorkerApplication.main(new String[] { "--spring.main.web-application-type=" + type, "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot run an incoming web server");
    }
}
