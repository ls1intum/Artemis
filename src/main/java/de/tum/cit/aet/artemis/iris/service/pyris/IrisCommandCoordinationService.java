package de.tum.cit.aet.artemis.iris.service.pyris;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisCommandAckDTO;

/**
 * Coordinates the synchronous wait for a client acknowledgement of an Iris command across the cluster.
 * <p>
 * A command is carried out by pushing a request to the user's browser over WebSocket and blocking until the browser replies. The browser's STOMP ack may arrive on a different node
 * than the one awaiting it, so acks are broadcast over a distributed topic and applied to the local pending future by correlation id. Only the node that registered a correlation
 * id
 * holds a future for it; all other nodes ignore the broadcast.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisCommandCoordinationService {

    private static final Logger log = LoggerFactory.getLogger(IrisCommandCoordinationService.class);

    private static final String ACK_TOPIC = "iris-command-ack";

    private final DistributedDataProvider distributedDataProvider;

    private final Map<String, PendingCommand> pendingCommands = new ConcurrentHashMap<>();

    public IrisCommandCoordinationService(DistributedDataProvider distributedDataProvider) {
        this.distributedDataProvider = distributedDataProvider;
    }

    @PostConstruct
    public void init() {
        distributedDataProvider.<AckMessage>getTopic(ACK_TOPIC).addMessageListener(this::applyAck);
    }

    /**
     * Registers a pending command awaiting a client ack. The returned future completes when the matching ack arrives on any node, or is completed exceptionally by the caller on
     * timeout. The pending entry is cleaned up automatically once the future settles.
     *
     * @param correlationId the unique id correlating request and ack
     * @param userLogin     the login of the user expected to send the ack (guards against acks from other users)
     * @return a future that completes with the client's ack
     */
    public CompletableFuture<IrisCommandAckDTO> register(String correlationId, String userLogin) {
        var future = new CompletableFuture<IrisCommandAckDTO>();
        pendingCommands.put(correlationId, new PendingCommand(future, userLogin));
        future.whenComplete((_, _) -> pendingCommands.remove(correlationId));
        return future;
    }

    /**
     * Broadcasts a client ack to the whole cluster so the node awaiting it can complete its pending future.
     *
     * @param ack       the ack received from the client
     * @param userLogin the login of the authenticated user that sent the ack
     */
    public void handleAck(IrisCommandAckDTO ack, String userLogin) {
        distributedDataProvider.<AckMessage>getTopic(ACK_TOPIC).publish(new AckMessage(ack.correlationId(), ack.applied(), userLogin));
    }

    private void applyAck(AckMessage message) {
        var pending = pendingCommands.get(message.correlationId());
        if (pending == null) {
            // Not awaiting on this node (or already timed out) — ignore.
            log.debug("Iris command ack {} has no pending future on this node (already timed out, or awaited elsewhere)", message.correlationId());
            return;
        }
        if (!pending.userLogin().equals(message.userLogin())) {
            log.warn("Ignoring Iris command ack for correlationId {} from unexpected user", message.correlationId());
            return;
        }
        log.debug("Completing pending Iris command {} with client ack (applied={})", message.correlationId(), message.applied());
        pending.future().complete(new IrisCommandAckDTO(message.correlationId(), message.applied()));
    }

    private record PendingCommand(CompletableFuture<IrisCommandAckDTO> future, String userLogin) {
    }

    private record AckMessage(String correlationId, boolean applied, String userLogin) implements Serializable {
    }
}
