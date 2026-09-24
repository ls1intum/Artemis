package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

/** Reads only this worker's commands. Failed admission is retried by the provider transport. */
@Component
@Profile(PROFILE_AIWORKER)
@Lazy(false)
public class WorkerCommandListener {

    private static final Logger log = LoggerFactory.getLogger(WorkerCommandListener.class);

    private final WorkerTransport transport;

    private final WorkerSettings settings;

    private final WorkerSupervisorService supervisor;

    private final DistributedDataProvider provider;

    private final AtomicBoolean unavailable = new AtomicBoolean();

    public WorkerCommandListener(WorkerTransport transport, WorkerSettings settings, WorkerSupervisorService supervisor, DistributedDataProvider provider) {
        this.transport = transport;
        this.settings = settings;
        this.supervisor = supervisor;
        this.provider = provider;
    }

    @Scheduled(fixedDelay = 1000)
    public void receive() {
        try {
            if (!provider.isConnectedToCluster()) {
                reportUnavailable("provider is not connected");
                return;
            }
            transport.receiveCommands(settings.id(), supervisor::isCurrentIncarnation, supervisor::accept);
            if (unavailable.getAndSet(false)) {
                log.info("AI Worker command delivery resumed");
            }
        }
        catch (RuntimeException failure) {
            reportUnavailable(failure.getClass().getSimpleName());
        }
    }

    private void reportUnavailable(String reason) {
        if (unavailable.compareAndSet(false, true)) {
            log.warn("AI Worker command delivery is unavailable: {}", reason);
        }
    }
}
