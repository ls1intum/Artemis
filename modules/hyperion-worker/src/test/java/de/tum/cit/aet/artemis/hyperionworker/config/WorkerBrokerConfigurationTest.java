package de.tum.cit.aet.artemis.hyperionworker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WorkerBrokerConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(WorkerBrokerConfiguration.class)
            .withPropertyValues("spring.artemis.user=worker", "spring.artemis.password=test-only");

    @Test
    void verifiedTlsConfiguresBoundedConnectionWithoutConnecting() {
        runner.withPropertyValues("spring.artemis.broker-url=tcp://broker.invalid:61617?sslEnabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ActiveMQConnectionFactory.class);
            assertThat(context.getBean(ActiveMQConnectionFactory.class).getCallTimeout()).isEqualTo(5_000);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "tcp://broker.invalid:61616", "tcp://broker.invalid:61617?sslEnabled=false", "tcp://broker.invalid:61617?sslEnabled=true&trustAll=true",
            "tcp://broker.invalid:61617?sslEnabled=true&verifyHost=false", "tcp://broker.invalid:61617?sslEnabled=true&sslEnabled=false",
            "tcp://broker.invalid:61617?sslEnabled=true&sslEnabled=true", "tcp://broker.invalid:61617?sslEnabled=true#ignored" })
    void unsafeTransportFailsBeforeWorkerAdmission(String url) {
        runner.withPropertyValues("spring.artemis.broker-url=" + url).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingCredentialsFailClosed() {
        runner.withPropertyValues("spring.artemis.broker-url=tcp://broker.invalid:61617?sslEnabled=true", "spring.artemis.password=")
                .run(context -> assertThat(context).hasFailed());
    }
}
