package de.tum.cit.aet.artemis.sharing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SharingHealthHistoryTest {

    @Test
    void laterEventsDoNotChangePreviousSnapshots() throws Exception {
        var connector = new SharingConnectorService(mock(RestClient.class), mock(TaskScheduler.class));
        connector.getPluginConfig(URI.create("https://sharing.example/?token=dummy-callback-credential").toURL(), Optional.of("Test installation"));
        var connected = connector.getHealthSnapshot();
        var lastConnect = connected.lastConnect();

        for (int i = 0; i < SharingConnectorService.HEALTH_HISTORY_LIMIT; i++) {
            connector.validateApiKey(null);
        }
        connector.shutDown();

        assertThat(connected.entries()).singleElement().satisfies(entry -> assertThat(entry.message()).doesNotContain("dummy-callback-credential"));
        assertThatThrownBy(() -> connected.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(connected.lastConnect()).isNotNull().isEqualTo(lastConnect);
        var disconnected = connector.getHealthSnapshot();
        assertThat(disconnected.lastConnect()).isNull();
        assertThat(disconnected.entries()).hasSize(SharingConnectorService.HEALTH_HISTORY_LIMIT);
        assertThat(disconnected.entries().getFirst().message()).contains("validation");
        assertThat(disconnected.entries().getLast().message()).isEqualTo("shutdown requested");
    }

    @Test
    void failedReinitializationKeepsCredentialsOutOfHealthAndLogs() {
        String dummyKey = "dummy-sharing-secret";
        String baseUrl = "https://sharing.example";
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var connector = new SharingConnectorService(builder.build(), mock(TaskScheduler.class));
        connector.setSharingApiKey(dummyKey);
        ReflectionTestUtils.setField(connector, "sharingUrl", baseUrl);
        server.expect(requestTo(baseUrl + "/api/pluginIF/v0.1/reInitialize?apiKey=" + dummyKey)).andRespond(request -> {
            throw new IOException("Cannot reach " + request.getURI());
        });
        var indicator = new SharingHealthIndicator(connector);
        var logger = (Logger) LoggerFactory.getLogger(SharingConnectorService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            connector.triggerReinit();
            server.verify();

            assertThat(indicator.health().getDetails().values().toString()).contains("Failed to request reinitialization").doesNotContain(dummyKey, baseUrl);
            assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("Failed to request reinitialization")).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(dummyKey, baseUrl);
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
