package de.tum.cit.aet.artemis.aiworker.service;

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

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.config.AiWorkerEnabled;
import de.tum.cit.aet.artemis.aiworker.config.AiWorkerProperties;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerState;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionClaimDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerStatusDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/** Discovers worker capacity through authenticated queues; only core nodes participate in the application grid. */
@Lazy
@Service
@Conditional(AiWorkerEnabled.class)
public class WorkerRegistryService {

    private final AiWorkerProperties properties;

    private final ConnectionFactory connections;

    private final WorkerMessageCodecApi codec;

    private final DistributedMap<String, Presence> presence;

    private final DistributedMap<String, UUID> leases;

    private final List<DefaultMessageListenerContainer> listeners = new ArrayList<>();

    public WorkerRegistryService(AiWorkerProperties properties, @Qualifier("aiWorkerConnectionFactory") ConnectionFactory connections, WorkerMessageCodecApi codec,
            DistributedDataProvider data) {
        this.properties = properties;
        this.connections = connections;
        this.codec = codec;
        presence = data.getExpiringMap("aiworker-presence", properties.presenceTtl());
        leases = data.getExpiringMap("aiworker-leases", properties.leaseTtl());
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

    void recordPresence(String destinationWorker, WorkerEventDTO event) {
        if (!destinationWorker.equals(event.workerId()) || event.type() != WorkerEventType.HEARTBEAT) {
            throw new IllegalArgumentException("Worker identity does not match its authenticated destination");
        }
        updatePresence(destinationWorker, event, true);
    }

    /**
     * Records post-cleanup capacity before the exact execution releases its claim, without waiting for another heartbeat.
     *
     * @param claim still-owned execution claim
     * @param event authenticated terminal event for that claim
     */
    public void recordCompletion(ExecutionClaimDTO claim, WorkerEventDTO event) {
        if (!claim.identity().equals(event.identity()) || !claim.imageDigest().equals(event.imageDigest()) || !claim.capability().equals(event.capability())
                || (event.type() != WorkerEventType.FINISHED && event.type() != WorkerEventType.CANCELLED && event.type() != WorkerEventType.ERROR)) {
            throw new IllegalArgumentException("Completion does not match the claimed execution and image");
        }
        if (claim.identity().executionId().equals(leases.get(slotKey(claim.identity())))) {
            updatePresence(claim.identity().workerId(), event, false);
        }
    }

    private void updatePresence(String destinationWorker, WorkerEventDTO event, boolean heartbeat) {
        List<SlotExecution> executions = event.capacity() != null ? event.capacity().executions().stream().map(SlotExecution::from).toList()
                : heartbeat && event.identity() != null ? List.of(SlotExecution.from(event.identity())) : List.of();
        int slots = event.capacity() == null ? 1 : event.capacity().slots();
        Presence updated = new Presence(event.incarnation(), event.sequence(), Instant.now(), event.imageDigest(), event.ready(), slots, executions, event.capability());
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

    public boolean hasAvailableSlot() {
        return properties.ids().stream().anyMatch(worker -> availableSlots(worker, presence.get(worker)) > 0);
    }

    /**
     * Checks capacity for an exact workload capability, independently of other worker pools.
     *
     * @param capability the required workload, schema version and profile
     * @return whether a compatible worker has a free slot
     */
    public boolean hasAvailableSlot(WorkloadCapabilityDTO capability) {
        return properties.ids().stream().anyMatch(worker -> {
            Presence value = presence.get(worker);
            return value != null && capability.equals(value.capability()) && availableSlots(worker, value) > 0;
        });
    }

    /**
     * @param capability required workload, schema version and profile
     * @param jobId      public job identifier
     * @param resourceId opaque workload-owned resource identifier
     * @return an exact-incarnation execution claim
     */
    public ExecutionClaimDTO claim(String jobId, String resourceId, WorkloadCapabilityDTO capability) {
        for (String worker : properties.ids()) {
            Presence observed = presence.get(worker);
            if (!available(observed) || !capability.equals(observed.capability())) {
                continue;
            }
            for (int slot = 0; slot < observed.slots(); slot++) {
                if (occupied(observed, slot)) {
                    continue;
                }
                UUID execution = UUID.randomUUID();
                var identity = new ExecutionIdentityDTO(jobId, resourceId, execution, worker, observed.incarnation(), slot);
                if (leases.putIfAbsent(slotKey(identity), execution) == null) {
                    return new ExecutionClaimDTO(identity, observed.imageDigest(), capability);
                }
            }
        }
        throw new ServiceUnavailableAlertException("No AI worker has available capacity.", "aiworker", "workerCapacityUnavailable");
    }

    /**
     * Extends only the still-owned execution while its worker incarnation is alive.
     *
     * @param claim exact execution claim
     * @return whether core still owns that execution
     */
    public boolean renew(ExecutionClaimDTO claim) {
        String worker = slotKey(claim.identity());
        Presence live = presence.get(claim.identity().workerId());
        if (live == null || !claim.identity().workerIncarnation().equals(live.incarnation()) || !claim.imageDigest().equals(live.imageDigest())
                || !claim.capability().equals(live.capability()) || !claim.identity().executionId().equals(leases.get(worker))) {
            return false;
        }
        return leases.refreshTimeToLive(worker, properties.leaseTtl()) && claim.identity().executionId().equals(leases.get(worker));
    }

    public void release(ExecutionClaimDTO claim) {
        leases.remove(slotKey(claim.identity()), claim.identity().executionId());
    }

    public int reachableWorkers() {
        return (int) properties.ids().stream().filter(worker -> presence.get(worker) != null).count();
    }

    /**
     * Counts workers with an engine, including workers currently occupied by an execution.
     *
     * @return live capable worker count
     */
    public int capableWorkers() {
        return (int) properties.ids().stream().map(presence::get)
                .filter(value -> value != null && (value.ready() || !value.executions().isEmpty()) && value.receivedAt().plus(properties.presenceTtl()).isAfter(Instant.now()))
                .count();
    }

    public int availableWorkers() {
        return (int) properties.ids().stream().filter(worker -> availableSlots(worker, presence.get(worker)) > 0).count();
    }

    /**
     * Returns a bounded diagnostic snapshot; it is not a reservation and must never be used for admission.
     *
     * @return one entry per configured worker, including workers whose heartbeat expired
     */
    public List<WorkerStatusDTO> workerStatuses() {
        Instant now = Instant.now();
        return properties.ids().stream().map(worker -> {
            Presence live = presence.get(worker);
            boolean leaseHeld = java.util.stream.IntStream.range(0, 16).anyMatch(slot -> leases.get(slotKey(worker, slot)) != null);
            if (live == null || !live.receivedAt().plus(properties.presenceTtl()).isAfter(now)) {
                return new WorkerStatusDTO(worker, WorkerState.OFFLINE, null, null, null, null, leaseHeld, 0, 0, List.of(), null);
            }
            WorkerState state;
            if (availableSlots(worker, live) > 0) {
                state = WorkerState.AVAILABLE;
            }
            else if (!live.executions().isEmpty()) {
                state = WorkerState.BUSY;
            }
            else if (leaseHeld) {
                state = WorkerState.RESERVED;
            }
            else {
                state = live.ready() ? WorkerState.AVAILABLE : WorkerState.NOT_READY;
            }
            return new WorkerStatusDTO(worker, state, live.receivedAt(), live.imageDigest(), live.incarnation(),
                    live.executions().isEmpty() ? null : live.executions().getFirst().executionId(), leaseHeld, live.slots(), availableSlots(worker, live),
                    live.executions().stream().map(execution -> new de.tum.cit.aet.artemis.aiworker.dto.WorkerExecutionDTO(execution.slot(), execution.executionId(),
                            execution.jobId(), execution.resourceId())).toList(),
                    live.capability());
        }).toList();
    }

    private boolean available(@Nullable Presence value) {
        return value != null && value.ready() && value.receivedAt().plus(properties.presenceTtl()).isAfter(Instant.now());
    }

    private int availableSlots(String worker, @Nullable Presence value) {
        if (!available(value)) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < value.slots(); slot++) {
            if (!occupied(value, slot) && leases.get(slotKey(worker, slot)) == null) {
                count++;
            }
        }
        return count;
    }

    private static boolean occupied(Presence value, int slot) {
        return value.executions().stream().anyMatch(execution -> execution.slot() == slot);
    }

    private static String slotKey(ExecutionIdentityDTO identity) {
        return slotKey(identity.workerId(), identity.slot());
    }

    private static String slotKey(String worker, int slot) {
        return slot == 0 ? worker : worker + ":" + slot;
    }

    public static String eventDestination(String worker) {
        return "aiworker." + worker + ".events";
    }

    /** Stops only listeners created by this registry. */
    @PreDestroy
    public void stop() {
        listeners.forEach(DefaultMessageListenerContainer::destroy);
    }

    private record Presence(UUID incarnation, long sequence, Instant receivedAt, String imageDigest, boolean ready, int slots, List<SlotExecution> executions,
            WorkloadCapabilityDTO capability) implements Serializable {
    }

    private record SlotExecution(int slot, UUID executionId, String jobId, String resourceId) implements Serializable {

        static SlotExecution from(ExecutionIdentityDTO identity) {
            return new SlotExecution(identity.slot(), identity.executionId(), identity.jobId(), identity.resourceId());
        }
    }

}
