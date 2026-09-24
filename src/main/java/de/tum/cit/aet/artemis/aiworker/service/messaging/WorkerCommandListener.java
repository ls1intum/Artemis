package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;

/** Reads only this worker's commands. Failed admission is retried by the provider transport. */
@Component
@Profile(PROFILE_AIWORKER)
@Lazy(false)
public class WorkerCommandListener {

    private final WorkerTransport transport;

    private final WorkerSettings settings;

    private final WorkerSupervisorService supervisor;

    public WorkerCommandListener(WorkerTransport transport, WorkerSettings settings, WorkerSupervisorService supervisor) {
        this.transport = transport;
        this.settings = settings;
        this.supervisor = supervisor;
    }

    @Scheduled(fixedDelay = 1000)
    public void receive() {
        transport.receiveCommands(settings.id(), supervisor::isCurrentIncarnation, supervisor::accept);
    }
}
