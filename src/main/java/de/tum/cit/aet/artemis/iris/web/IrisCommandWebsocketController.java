package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisCommandAckDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.IrisCommandCoordinationService;

/**
 * Receives the client's acknowledgement of an Iris command over STOMP and hands it to the coordination service, which completes the (possibly remote) node that is awaiting it.
 */
@Controller
@Lazy
@Conditional(IrisEnabled.class)
public class IrisCommandWebsocketController {

    private static final Logger log = LoggerFactory.getLogger(IrisCommandWebsocketController.class);

    private final IrisCommandCoordinationService coordinationService;

    public IrisCommandWebsocketController(IrisCommandCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    /**
     * Handles a client ack for a pending Iris command. The authenticated principal guards against acks spoofed on behalf of another user.
     *
     * @param ack       the ack payload sent by the client
     * @param principal the authenticated user sending the ack
     */
    @MessageMapping("topic/iris/command-ack")
    public void acknowledgeCommand(@Payload IrisCommandAckDTO ack, Principal principal) {
        if (ack == null || !isUuid(ack.correlationId())) {
            // Only server-generated UUIDs can match a command. Reject arbitrary client input before it is amplified
            // over the reliable cluster topic, and do not include a potentially oversized value in the log.
            log.warn("Ignoring Iris command ack with an invalid correlation id");
            return;
        }
        if (principal == null) {
            // Without an authenticated principal the ack cannot be attributed to the user awaiting it; drop it.
            log.warn("Ignoring Iris command ack {} without an authenticated principal", ack.correlationId());
            return;
        }
        log.debug("Received client command ack {} from user {} (applied={})", ack.correlationId(), principal.getName(), ack.applied());
        coordinationService.handleAck(ack, principal.getName());
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        }
        catch (IllegalArgumentException _) {
            return false;
        }
    }
}
