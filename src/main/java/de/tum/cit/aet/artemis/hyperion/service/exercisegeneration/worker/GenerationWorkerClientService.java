package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import java.util.function.Consumer;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService.Claim;

/** Sends job-level commands and consumes events for one exact execution; never exposes sandbox operations. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationWorkerClientService {

    private final JmsTemplate messaging;

    private final WorkerMessageCodec codec;

    public GenerationWorkerClientService(@Qualifier("hyperionConnectionFactory") ConnectionFactory connections, WorkerMessageCodec codec) {
        this.codec = codec;
        messaging = new JmsTemplate(connections);
        messaging.setSessionTransacted(true);
        messaging.setDeliveryPersistent(true);
        messaging.setExplicitQosEnabled(true);
        messaging.setTimeToLive(60_000);
    }

    public void send(WorkerCommand command) {
        messaging.convertAndSend("hyperion.worker." + command.identity().workerId() + ".commands", codec.encode(command));
    }

    /**
     * Consumes at most one event and commits its broker delivery only after the core callback completes.
     * A core process loss does not transfer this execution to another task.
     *
     * @param claim the caller's exact worker assignment
     * @param apply accepts validated worker evidence
     */
    public void receive(Claim claim, Consumer<WorkerEvent> apply) {
        messaging.execute(session -> {
            String selector = "executionId = '" + claim.identity().executionId() + "' AND eventType <> 'HEARTBEAT'";
            try (var consumer = session.createConsumer(session.createQueue(GenerationWorkerRegistryService.eventDestination(claim.identity().workerId())), selector)) {
                var message = consumer.receive(1_000);
                if (message == null) {
                    session.commit();
                    return null;
                }
                if (!(message instanceof TextMessage text)) {
                    throw new IllegalArgumentException("Worker events must be JSON text messages");
                }
                WorkerEvent event = codec.decodeEvent(text.getText());
                if (!claim.identity().equals(event.identity()) || !claim.imageDigest().equals(event.imageDigest())) {
                    throw new IllegalArgumentException("Worker result does not match the claimed execution and image");
                }
                apply.accept(event);
                session.commit();
                return null;
            }
            catch (RuntimeException | JMSException failure) {
                try {
                    session.rollback();
                }
                catch (JMSException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }, true);
    }
}
