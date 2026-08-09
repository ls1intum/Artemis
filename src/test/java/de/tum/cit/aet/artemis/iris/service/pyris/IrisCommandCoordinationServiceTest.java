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
            return UUID.randomUUID();
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
        var correlationId = UUID.randomUUID().toString();
        CompletableFuture<IrisCommandAckDTO> future = coordinationService.register(correlationId, "student1");

        coordinationService.handleAck(new IrisCommandAckDTO(correlationId, true), "student1");

        var ack = future.get(1, TimeUnit.SECONDS);
        assertThat(ack.correlationId()).isEqualTo(correlationId);
        assertThat(ack.applied()).isTrue();

        // A second ack for the same command — a duplicate, or one racing the timeout — must not flip or fail the
        // result the pipeline already acted on. Note this pins the outcome, not the mechanism: the entry is gone by
        // now, but even if it were not, completing an already-completed future is a no-op.
        coordinationService.handleAck(new IrisCommandAckDTO(correlationId, false), "student1");
        assertThat(future.get(1, TimeUnit.SECONDS).applied()).isTrue();
    }

    @Test
    void handleAck_thatMatchesNoPendingRegistrationIsIgnored() {
        var correlationId = UUID.randomUUID().toString();
        CompletableFuture<IrisCommandAckDTO> future = coordinationService.register(correlationId, "student1");

        coordinationService.handleAck(new IrisCommandAckDTO(correlationId, true), "attacker");
        // No pending registration on this node for that id: the broadcast must be ignored without throwing.
        coordinationService.handleAck(new IrisCommandAckDTO(UUID.randomUUID().toString(), true), "student1");

        assertThat(future).isNotDone();
    }
}
