package de.tum.cit.aet.artemis.hyperionworker.messaging;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperionworker.session.WorkerSupervisor;

/** A short-lived command listener; poison messages use the broker's bounded redelivery/dead-letter policy. */
@Component
public class WorkerCommandListener {

    private final WorkerMessageCodec codec;

    private final WorkerSupervisor supervisor;

    public WorkerCommandListener(WorkerMessageCodec codec, WorkerSupervisor supervisor) {
        this.codec = codec;
        this.supervisor = supervisor;
    }

    @JmsListener(destination = "hyperion.worker.${artemis.hyperion.worker.id}.commands")
    public void receive(String body) {
        supervisor.accept(codec.decodeCommand(body));
    }
}
