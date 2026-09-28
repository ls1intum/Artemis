package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.websocket.GzipMessageConverter.COMPRESSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.localci.web.LocalCIWebsocketTopics;
import de.tum.cit.aet.artemis.programming.web.ProgrammingWebsocketTopics;

/**
 * Which messages are compressed, and what deciding that is allowed to cost.
 * <p>
 * The cost is the point of most of this. Emptiness used to be established by rendering the payload with
 * {@code toString()} and asking whether the result was empty, for every message on every topic, and the string was
 * then discarded. A 1000-student exam spent 4.3% of the Artemis nodes' on-CPU samples doing it.
 */
class WebsocketMessagingServiceCompressionTest {

    private static final WebsocketDestination COMPRESSIBLE = LocalCIWebsocketTopics.ADMIN_QUEUED_JOBS.at();

    private static final WebsocketDestination ORDINARY = ProgrammingWebsocketTopics.EXERCISE_SUBMISSIONS.at(42L);

    private SimpMessageSendingOperations messagingTemplate;

    private WebsocketMessagingService websocketMessagingService;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessageSendingOperations.class);
        websocketMessagingService = new WebsocketMessagingService(messagingTemplate, Runnable::run);
    }

    /**
     * Sends the payload and returns whether it went out with the compression header.
     */
    private boolean isSentCompressed(WebsocketDestination destination, Object payload) {
        websocketMessagingService.sendMessage(destination, payload);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headers = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq(destination.value()), (Object) any(), headers.capture());
        setUp();
        return headers.getValue().equals(COMPRESSION_HEADER);
    }

    /** A payload that records every time something renders it. */
    private static final class CountingPayload {

        private final AtomicInteger renders = new AtomicInteger();

        @Override
        public String toString() {
            renders.incrementAndGet();
            return "rendered";
        }
    }

    @Test
    @DisplayName("An ordinary message is never rendered to a String just to decide against compressing it")
    void ordinaryTopicDoesNotRenderThePayload() {
        CountingPayload payload = new CountingPayload();

        assertThat(isSentCompressed(ORDINARY, payload)).isFalse();
        assertThat(payload.renders).hasValue(0);
    }

    @Test
    @DisplayName("A compressible message is not rendered either")
    void compressibleTopicDoesNotRenderThePayload() {
        CountingPayload payload = new CountingPayload();

        assertThat(isSentCompressed(COMPRESSIBLE, payload)).isTrue();
        assertThat(payload.renders).hasValue(0);
    }

    @Test
    @DisplayName("Only topics declared with compression are compressed: the build queue and build agent topics")
    void onlyTopicsDeclaredWithCompressionAreCompressed() {
        Object payload = new CountingPayload();

        assertThat(isSentCompressed(LocalCIWebsocketTopics.COURSE_QUEUED_JOBS.at(7L), payload)).isTrue();
        assertThat(isSentCompressed(LocalCIWebsocketTopics.COURSE_RUNNING_JOBS.at(7L), payload)).isTrue();
        assertThat(isSentCompressed(LocalCIWebsocketTopics.ADMIN_BUILD_AGENTS.at(), payload)).isTrue();
        assertThat(isSentCompressed(LocalCIWebsocketTopics.ADMIN_BUILD_AGENT.at("agent-1"), payload)).isTrue();

        assertThat(isSentCompressed(ORDINARY, payload)).isFalse();
        assertThat(isSentCompressed(LocalCIWebsocketTopics.COURSE_FINISHED_JOBS.at(7L), payload)).isFalse();
        var declaredWithCompression = WebsocketTopic.of("/topic/test/compressed", WebsocketTopicAccess.anyAuthenticatedUser()).withCompression();
        assertThat(isSentCompressed(declaredWithCompression.at(), payload)).isTrue();
    }

    @Test
    @DisplayName("Nothing worth compressing is still not compressed")
    void emptyPayloadsAreNotCompressed() {
        assertThat(isSentCompressed(COMPRESSIBLE, null)).isFalse();
        assertThat(isSentCompressed(COMPRESSIBLE, "")).isFalse();
        assertThat(isSentCompressed(COMPRESSIBLE, List.of())).isFalse();
        assertThat(isSentCompressed(COMPRESSIBLE, Map.of())).isFalse();

        assertThat(isSentCompressed(COMPRESSIBLE, "queued")).isTrue();
        assertThat(isSentCompressed(COMPRESSIBLE, List.of("a"))).isTrue();
        assertThat(isSentCompressed(COMPRESSIBLE, Map.of("a", 1))).isTrue();
    }
}
