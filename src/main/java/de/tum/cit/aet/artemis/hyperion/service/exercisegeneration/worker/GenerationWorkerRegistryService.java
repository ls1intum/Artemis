package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.TextMessage;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.config.HyperionWorkerProperties;
import de.tum.cit.aet.artemis.hyperion.domain.GenerationWorkerState;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationWorkerStatusDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;

/** Discovers worker capacity through authenticated queues; only core nodes participate in the application grid. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationWorkerRegistryService {

    private final HyperionWorkerProperties properties;

    private final ConnectionFactory connections;

    private final WorkerMessageCodec codec;

    private final DistributedMap<String, Presence> presence;

    private final DistributedMap<String, UUID> leases;

    private final List<DefaultMessageListenerContainer> listeners = new ArrayList<>();

    public GenerationWorkerRegistryService(HyperionWorkerProperties properties, @Qualifier("hyperionConnectionFactory") ConnectionFactory connections, WorkerMessageCodec codec,
            DistributedDataProvider data) {
        this.properties = properties;
        this.connections = connections;
        this.codec = codec;
        presence = data.getExpiringMap("hyperion-worker-presence", properties.presenceTtl());
        leases = data.getExpiringMap("hyperion-worker-leases", properties.leaseTtl());
    }

    /** Heartbeats are load-balanced across core nodes; job events are consumed only by the owning task. */
    @PostConstruct
    public void start() {
        for (String workerId : properties.ids()) {
            var listener = new DefaultMessageListenerContainer();
            listener.setConnectionFactory(connections);
            listener.setDestinationName(eventDestination(workerId));
            listener.setMessageSelector("eventType = 'HEARTBEAT'");
            listener.setSessionTransacted(true);
            listener.setMessageListener((jakarta.jms.MessageListener) message -> {
                try {
                    if (!(message instanceof TextMessage text)) {
                        throw new IllegalArgumentException("Worker events must use JSON text messages");
                    }
                    recordPresence(workerId, codec.decodeEvent(text.getText()));
                }
                catch (jakarta.jms.JMSException e) {
                    throw new IllegalStateException("Could not read worker heartbeat", e);
                }
            });
            listener.initialize();
            listener.start();
            listeners.add(listener);
        }
    }

    void recordPresence(String destinationWorker, WorkerEvent event) {
        if (!destinationWorker.equals(event.workerId()) || event.type() != WorkerEvent.Type.HEARTBEAT) {
            throw new IllegalArgumentException("Worker identity does not match its authenticated destination");
        }
        updatePresence(destinationWorker, event, event.identity() == null ? null : event.identity().executionId());
    }

    /**
     * Records post-cleanup capacity before the exact execution releases its claim, without waiting for another heartbeat.
     *
     * @param claim still-owned execution claim
     * @param event authenticated terminal event for that claim
     */
    public void recordCompletion(Claim claim, WorkerEvent event) {
        if (!claim.identity().equals(event.identity()) || !claim.imageDigest().equals(event.imageDigest())
                || (event.type() != WorkerEvent.Type.FINISHED && event.type() != WorkerEvent.Type.CANCELLED && event.type() != WorkerEvent.Type.ERROR)) {
            throw new IllegalArgumentException("Completion does not match the claimed execution and image");
        }
        if (claim.identity().executionId().equals(leases.get(claim.identity().workerId()))) {
            updatePresence(claim.identity().workerId(), event, null);
        }
    }

    private void updatePresence(String destinationWorker, WorkerEvent event, @Nullable UUID activeExecution) {
        Presence updated = new Presence(event.incarnation(), event.sequence(), Instant.now(), event.imageDigest(), event.ready(), activeExecution);
        for (int attempt = 0; attempt < 3; attempt++) {
            Presence current = presence.get(destinationWorker);
            if (current == null) {
                if (presence.putIfAbsent(destinationWorker, updated) == null) {
                    return;
                }
            }
            else {
                // A restart waits for the old incarnation to expire; a second live process cannot steal the same worker ID.
                if (!current.incarnation().equals(event.incarnation()) || current.sequence() >= event.sequence()) {
                    return;
                }
                if (presence.replace(destinationWorker, current, updated)) {
                    presence.refreshTimeToLive(destinationWorker, properties.presenceTtl());
                    return;
                }
            }
        }
        throw new IllegalStateException("Worker presence changed concurrently; retry delivery");
    }

    public boolean hasAvailableGenerationSandboxSlot() {
        return properties.ids().stream().anyMatch(worker -> available(presence.get(worker)) && leases.get(worker) == null);
    }

    /**
     * @param jobId      public job identifier
     * @param exerciseId guarded exercise identifier
     * @return an exact-incarnation execution claim
     */
    public Claim claim(String jobId, long exerciseId) {
        for (String worker : properties.ids()) {
            Presence observed = presence.get(worker);
            if (!available(observed)) {
                continue;
            }
            UUID execution = UUID.randomUUID();
            if (leases.putIfAbsent(worker, execution) == null) {
                return new Claim(new ExecutionIdentity(jobId, exerciseId, execution, worker, observed.incarnation()), observed.imageDigest());
            }
        }
        throw new ServiceUnavailableAlertException("No generation worker has available capacity.", "hyperionExerciseGeneration", "generationCapacityUnavailable");
    }

    /**
     * Extends only the still-owned execution while its worker incarnation is alive.
     *
     * @param claim exact execution claim
     * @return whether core still owns that execution
     */
    public boolean renew(Claim claim) {
        String worker = claim.identity().workerId();
        Presence live = presence.get(worker);
        if (live == null || !claim.identity().workerIncarnation().equals(live.incarnation()) || !claim.identity().executionId().equals(leases.get(worker))) {
            return false;
        }
        return leases.refreshTimeToLive(worker, properties.leaseTtl()) && claim.identity().executionId().equals(leases.get(worker));
    }

    public void release(Claim claim) {
        leases.remove(claim.identity().workerId(), claim.identity().executionId());
    }

    public int reachableWorkers() {
        return (int) properties.ids().stream().filter(worker -> presence.get(worker) != null).count();
    }

    /**
     * Counts workers with an engine, including workers currently occupied by a generation.
     *
     * @return live capable worker count
     */
    public int capableWorkers() {
        return (int) properties.ids().stream().map(presence::get)
                .filter(value -> value != null && (value.ready() || value.activeExecution() != null) && value.receivedAt().plus(properties.presenceTtl()).isAfter(Instant.now()))
                .count();
    }

    public int availableWorkers() {
        return (int) properties.ids().stream().filter(worker -> available(presence.get(worker)) && leases.get(worker) == null).count();
    }

    /**
     * Returns a bounded diagnostic snapshot; it is not a reservation and must never be used for admission.
     *
     * @return one entry per configured worker, including workers whose heartbeat expired
     */
    public List<GenerationWorkerStatusDTO> workerStatuses() {
        Instant now = Instant.now();
        return properties.ids().stream().map(worker -> {
            Presence live = presence.get(worker);
            boolean leaseHeld = leases.get(worker) != null;
            if (live == null || !live.receivedAt().plus(properties.presenceTtl()).isAfter(now)) {
                return new GenerationWorkerStatusDTO(worker, GenerationWorkerState.OFFLINE, null, null, null, null, leaseHeld);
            }
            GenerationWorkerState state;
            if (live.activeExecution() != null) {
                state = GenerationWorkerState.BUSY;
            }
            else if (leaseHeld) {
                state = GenerationWorkerState.RESERVED;
            }
            else {
                state = live.ready() ? GenerationWorkerState.AVAILABLE : GenerationWorkerState.NOT_READY;
            }
            return new GenerationWorkerStatusDTO(worker, state, live.receivedAt(), live.imageDigest(), live.incarnation(), live.activeExecution(), leaseHeld);
        }).toList();
    }

    private boolean available(@Nullable Presence value) {
        return value != null && value.ready() && value.activeExecution() == null && value.receivedAt().plus(properties.presenceTtl()).isAfter(Instant.now());
    }

    public static String eventDestination(String worker) {
        return "hyperion.worker." + worker + ".events";
    }

    /** Stops only listeners created by this registry. */
    @PreDestroy
    public void stop() {
        listeners.forEach(DefaultMessageListenerContainer::destroy);
    }

    public record Claim(ExecutionIdentity identity, String imageDigest) {
    }

    private record Presence(UUID incarnation, long sequence, Instant receivedAt, String imageDigest, boolean ready, @Nullable UUID activeExecution) implements Serializable {
    }
}
