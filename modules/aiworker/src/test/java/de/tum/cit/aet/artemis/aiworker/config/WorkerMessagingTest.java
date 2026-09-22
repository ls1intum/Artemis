package de.tum.cit.aet.artemis.aiworker.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;

class WorkerMessagingTest {

    @ParameterizedTest
    @EnumSource(WorkerEventType.class)
    void onlyHeartbeatsExpire(WorkerEventType type) throws Exception {
        var connectionFactory = mock(ConnectionFactory.class);
        var connection = mock(Connection.class);
        var session = mock(Session.class);
        var queue = mock(Queue.class);
        var producer = mock(MessageProducer.class);
        var message = mock(TextMessage.class);
        var settings = mock(WorkerSettings.class);
        var codec = mock(WorkerMessageCodecApi.class);
        var event = mock(WorkerEventDTO.class);
        when(connectionFactory.createConnection()).thenReturn(connection);
        when(connection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(session);
        when(settings.id()).thenReturn("worker-1");
        when(event.type()).thenReturn(type);
        when(codec.encode(event)).thenReturn("event");
        when(session.createTextMessage("event")).thenReturn(message);
        when(session.createQueue("aiworker.worker-1.events")).thenReturn(queue);
        when(session.createProducer(queue)).thenReturn(producer);

        new WorkerMessaging().workerEventPublisher(connectionFactory, settings, codec).publish(event);

        verify(producer).send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, type == WorkerEventType.HEARTBEAT ? 10_000 : 0);
        verify(message).setStringProperty("eventType", type.name());
        verify(producer).close();
    }
}
