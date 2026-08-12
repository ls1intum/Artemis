package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;

import de.tum.cit.aet.artemis.iris.dto.IrisCommandAckDTO;

/**
 * Unit tests for {@link IrisCommandCoordinationService}. The Hazelcast topic is mocked and its
 * fan-out is simulated by delivering every published ack straight to the registered listener, so
 * the register -> handleAck -> future-completion round-trip (and its user/correlation guards) can be
 * exercised without a real cluster.
 */
@ExtendWith(MockitoExtension.class)
class IrisCommandCoordinationServiceTest {

    // The service treats correlation ids as opaque keys, so fixed values keep the tests deterministic and let a
    // failure name the same id on every run.
    private static final String CORRELATION_ID = "00000000-0000-0000-0000-000000000001";

    private static final String UNREGISTERED_CORRELATION_ID = "00000000-0000-0000-0000-000000000002";

    // Hazelcast hands back a registration id when a listener is added; nothing here uses it.
    private static final UUID LISTENER_REGISTRATION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private ITopic<Object> ackTopic;

    // The listener the service registers in init(), captured so the test can deliver published acks to
    // it — standing in for Hazelcast's same-node fan-out.
    private final AtomicReference<MessageListener<Object>> listenerRef = new AtomicReference<>();

    private IrisCommandCoordinationService coordinationService;

    @BeforeEach
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void setUp() {
        when(hazelcastInstance.getTopic(any())).thenReturn((ITopic) ackTopic);
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return LISTENER_REGISTRATION_ID;
        }).when(ackTopic).addMessageListener(any());
        doAnswer(invocation -> {
            Object published = invocation.getArgument(0);
            MessageListener<Object> listener = listenerRef.get();
            if (listener != null) {
                Message<Object> message = mock(Message.class);
                when(message.getMessageObject()).thenReturn(published);
                listener.onMessage(message);
            }
            return null;
        }).when(ackTopic).publish(any());

        coordinationService = new IrisCommandCoordinationService(hazelcastInstance);
        coordinationService.init();
    }

    @Test
    void register_completesFutureWhenMatchingAckArrivesAndKeepsThatResult() throws Exception {
        CompletableFuture<IrisCommandAckDTO> future = coordinationService.register(CORRELATION_ID, "student1");

        coordinationService.handleAck(new IrisCommandAckDTO(CORRELATION_ID, true), "student1");

        var ack = future.get(1, TimeUnit.SECONDS);
        assertThat(ack.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(ack.applied()).isTrue();

        // A second ack for the same command — a duplicate, or one racing the timeout — must not flip or fail the
        // result the pipeline already acted on. Note this pins the outcome, not the mechanism: the entry is gone by
        // now, but even if it were not, completing an already-completed future is a no-op.
        coordinationService.handleAck(new IrisCommandAckDTO(CORRELATION_ID, false), "student1");
        assertThat(future.get(1, TimeUnit.SECONDS).applied()).isTrue();
    }

    @Test
    void handleAck_thatMatchesNoPendingRegistrationIsIgnored() {
        CompletableFuture<IrisCommandAckDTO> future = coordinationService.register(CORRELATION_ID, "student1");

        coordinationService.handleAck(new IrisCommandAckDTO(CORRELATION_ID, true), "attacker");
        // No pending registration on this node for that id: the broadcast must be ignored without throwing.
        coordinationService.handleAck(new IrisCommandAckDTO(UNREGISTERED_CORRELATION_ID, true), "student1");

        assertThat(future).isNotDone();
    }
}
