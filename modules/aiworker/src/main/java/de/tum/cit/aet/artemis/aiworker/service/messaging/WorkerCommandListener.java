package de.tum.cit.aet.artemis.aiworker.service.messaging;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;

/** A short-lived command listener; poison messages use the broker's bounded redelivery/dead-letter policy. */
@Component
public class WorkerCommandListener {

    private final WorkerMessageCodecApi codec;

    private final WorkerSupervisorService supervisor;

    public WorkerCommandListener(WorkerMessageCodecApi codec, WorkerSupervisorService supervisor) {
        this.codec = codec;
        this.supervisor = supervisor;
    }

    @JmsListener(destination = "aiworker.${artemis.aiworker.id}.commands")
    public void receive(String body) {
        supervisor.accept(codec.decodeCommand(body));
    }
}
