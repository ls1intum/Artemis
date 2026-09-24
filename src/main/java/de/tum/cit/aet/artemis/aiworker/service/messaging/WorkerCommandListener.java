package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;

/** A short-lived command listener; poison messages use the broker's bounded redelivery/dead-letter policy. */
@Component
@Profile(PROFILE_AIWORKER)
@Lazy(false)
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
