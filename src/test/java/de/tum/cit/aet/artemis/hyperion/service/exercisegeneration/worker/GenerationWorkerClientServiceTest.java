package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService.Claim;

class GenerationWorkerClientServiceTest {

    private final ConnectionFactory factory = mock(ConnectionFactory.class);

    private final Connection connection = mock(Connection.class);

    private final Session session = mock(Session.class);

    private final MessageConsumer consumer = mock(MessageConsumer.class);

    private final Queue queue = mock(Queue.class);

    private final WorkerMessageCodec codec = new WorkerMessageCodec();

    private final ExecutionIdentity identity = new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker", UUID.randomUUID());

    private final String digest = "sha256:" + "a".repeat(64);

    private final Claim claim = new Claim(identity, digest);

    private final GenerationWorkerClientService client = new GenerationWorkerClientService(factory, codec);

    @BeforeEach
    void setup() throws Exception {
        when(factory.createConnection()).thenReturn(connection);
        when(connection.createSession(true, Session.AUTO_ACKNOWLEDGE)).thenReturn(session);
        when(session.createQueue("hyperion.worker.worker.events")).thenReturn(queue);
        when(session.createConsumer(eq(queue), anyString())).thenReturn(consumer);
    }

    @Test
    void commitsOnlyAfterCallbackCompletes() throws Exception {
        WorkerEvent event = event(identity, digest);
        deliver(event);
        Consumer<WorkerEvent> callback = mock();
        client.receive(claim, callback);
        var order = inOrder(callback, session);
        order.verify(callback).accept(event);
        order.verify(session).commit();
        verify(session, never()).rollback();
        verify(session).createConsumer(queue, "executionId = '" + identity.executionId() + "' AND eventType <> 'HEARTBEAT'");
        verify(consumer).close();
    }

    @Test
    void failedCoreCallbackRollsBackDelivery() throws Exception {
        deliver(event(identity, digest));
        var failure = new IllegalStateException("checkpoint store unavailable");
        assertThatThrownBy(() -> client.receive(claim, event -> {
            throw failure;
        })).isSameAs(failure);
        verify(session).rollback();
        verify(session, never()).commit();
        verify(consumer).close();
    }

    @Test
    void rejectsOtherAssignmentBeforeCallback() throws Exception {
        deliver(event(new ExecutionIdentity("new-job", 1, UUID.randomUUID(), "worker", identity.workerIncarnation()), digest));
        assertRejected();
    }

    @Test
    void rejectsOtherImageBeforeCallback() throws Exception {
        deliver(event(identity, "sha256:" + "b".repeat(64)));
        assertRejected();
    }

    @Test
    void rejectsNonTextDelivery() throws Exception {
        when(consumer.receive(1_000)).thenReturn(mock(Message.class));
        assertRejected();
    }

    @Test
    void emptyPollCommitsWithoutCallingConsumer() throws Exception {
        client.receive(claim, event -> {
            throw new AssertionError("no event expected");
        });
        verify(session).commit();
        verify(session, never()).rollback();
    }

    private void assertRejected() throws Exception {
        Consumer<WorkerEvent> callback = mock();
        assertThatThrownBy(() -> client.receive(claim, callback)).isInstanceOf(IllegalArgumentException.class);
        verify(session).rollback();
        verify(session, never()).commit();
        verifyNoInteractions(callback);
    }

    private void deliver(WorkerEvent event) throws Exception {
        TextMessage text = mock(TextMessage.class);
        when(text.getText()).thenReturn(codec.encode(event));
        when(consumer.receive(1_000)).thenReturn(text);
    }

    private WorkerEvent event(ExecutionIdentity execution, String image) {
        return new WorkerEvent(de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand.PROTOCOL_VERSION, "worker", identity.workerIncarnation(), 1, Instant.now(),
                WorkerEvent.Type.STARTED, execution, false, image, null, null, null);
    }
}
