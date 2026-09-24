package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Which messages are compressed, and what deciding that is allowed to cost.
 * <p>
 * The cost is the point of most of this. Emptiness used to be established by rendering the payload with
 * {@code toString()} and asking whether the result was empty, for every message on every topic, and the string was
 * then discarded. A 1000-student exam spent 4.3% of the Artemis nodes' on-CPU samples doing it.
 */
class WebsocketMessagingServiceCompressionTest {

    private static final String COMPRESSIBLE = "/topic/admin/queued-jobs";

    private static final String ORDINARY = "/topic/exercise/42/newSubmissions";

    private boolean shouldCompress(String topic, Object payload) {
        return (boolean) ReflectionTestUtils.invokeMethod(WebsocketMessagingService.class, "shouldCompress", topic, payload);
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

        assertThat(shouldCompress(ORDINARY, payload)).isFalse();
        assertThat(payload.renders).hasValue(0);
    }

    @Test
    @DisplayName("A compressible message is not rendered either")
    void compressibleTopicDoesNotRenderThePayload() {
        CountingPayload payload = new CountingPayload();

        assertThat(shouldCompress(COMPRESSIBLE, payload)).isTrue();
        assertThat(payload.renders).hasValue(0);
    }

    @Test
    @DisplayName("Only the build queue and build agent topics are compressed")
    void onlyTheBuildTopicsAreCompressed() {
        Object payload = new CountingPayload();

        assertThat(shouldCompress("/topic/courses/7/queued-jobs", payload)).isTrue();
        assertThat(shouldCompress("/topic/courses/7/running-jobs", payload)).isTrue();
        assertThat(shouldCompress("/topic/admin/build-agents", payload)).isTrue();
        assertThat(shouldCompress("/topic/admin/build-agent/agent-1", payload)).isTrue();

        assertThat(shouldCompress(ORDINARY, payload)).isFalse();
        assertThat(shouldCompress("/topic/courses/7/exercises", payload)).isFalse();
        assertThat(shouldCompress(null, payload)).isFalse();
    }

    @Test
    @DisplayName("Nothing worth compressing is still not compressed")
    void emptyPayloadsAreNotCompressed() {
        assertThat(shouldCompress(COMPRESSIBLE, null)).isFalse();
        assertThat(shouldCompress(COMPRESSIBLE, "")).isFalse();
        assertThat(shouldCompress(COMPRESSIBLE, List.of())).isFalse();
        assertThat(shouldCompress(COMPRESSIBLE, Map.of())).isFalse();

        assertThat(shouldCompress(COMPRESSIBLE, "queued")).isTrue();
        assertThat(shouldCompress(COMPRESSIBLE, List.of("a"))).isTrue();
        assertThat(shouldCompress(COMPRESSIBLE, Map.of("a", 1))).isTrue();
    }
}
