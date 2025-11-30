package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.core.dto.WebsocketNodeDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.messaging.WebsocketBrokerReconnectionMessagingService;
import de.tum.cit.aet.artemis.core.service.messaging.WebsocketBrokerReconnectionService;

@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/core/admin/websocket/")
public class AdminWebsocketResource {

    private static final Logger log = LoggerFactory.getLogger(AdminWebsocketResource.class);

    private final WebsocketBrokerReconnectionService websocketBrokerReconnectionService;

    private final WebsocketBrokerReconnectionMessagingService websocketBrokerReconnectionMessagingService;

    private final HazelcastInstance hazelcastInstance;

    public AdminWebsocketResource(WebsocketBrokerReconnectionService websocketBrokerReconnectionService,
            WebsocketBrokerReconnectionMessagingService websocketBrokerReconnectionMessagingService, HazelcastInstance hazelcastInstance) {
        this.websocketBrokerReconnectionService = websocketBrokerReconnectionService;
        this.websocketBrokerReconnectionMessagingService = websocketBrokerReconnectionMessagingService;
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * GET core/admin/websocket/nodes: returns the hazelcast members (id and address).
     *
     * @return list of websocket nodes with metadata used by the admin UI
     */
    @GetMapping("nodes")
    public ResponseEntity<Iterable<WebsocketNodeDTO>> getWebsocketNodes() {
        var cluster = hazelcastInstance.getCluster();
        var localId = cluster.getLocalMember().getUuid().toString();
        var brokerStatus = hazelcastInstance.<String, Boolean>getMap(WebsocketBrokerReconnectionService.WEBSOCKET_BROKER_STATUS_MAP);
        var nodes = cluster.getMembers().stream().map(member -> {
            String memberId = member.getUuid().toString();
            String instanceId = member.getAttribute("instanceId");
            boolean isLocal = memberId.equals(localId);
            boolean brokerConnected = Boolean.TRUE.equals(brokerStatus.get(memberId));
            return new WebsocketNodeDTO(memberId, member.getAddress().toString(), member.getAddress().getHost(), member.getAddress().getPort(), isLocal, member.isLiteMember(),
                    instanceId, brokerConnected);
        }).toList();
        return ResponseEntity.ok(nodes);
    }

    /**
     * POST core/admin/websocket/reconnect: manually trigger reconnect attempts to the external websocket broker.
     *
     * @param targetNodeId optional hazelcast member id. If omitted, all nodes will reconnect.
     * @param action       desired control action (RECONNECT, DISCONNECT, CONNECT)
     * @return 202 (Accepted) if reconnect attempts were scheduled, 503 (Service Unavailable) otherwise
     */
    @PostMapping("reconnect")
    public ResponseEntity<Void> triggerReconnect(@RequestParam(value = "targetNodeId", required = false) String targetNodeId,
            @RequestParam(value = "action", required = false, defaultValue = "RECONNECT") String action) {
        String requester = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        log.info("REST request to trigger websocket broker action {} for target {} by {}", action, targetNodeId, requester);

        var cluster = hazelcastInstance.getCluster();
        var localMemberId = cluster.getLocalMember().getUuid().toString();
        var targetMembers = cluster.getMembers().stream().filter(member -> !member.isLiteMember())
                .filter(member -> targetNodeId == null || targetNodeId.isBlank() || member.getUuid().toString().equals(targetNodeId)).toList();

        if (targetMembers.isEmpty()) {
            log.info("No core websocket nodes matched reconnect request for target {}", targetNodeId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        WebsocketBrokerReconnectionService.ControlAction controlAction;
        try {
            controlAction = WebsocketBrokerReconnectionService.ControlAction.valueOf(action.toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        targetMembers.forEach(member -> websocketBrokerReconnectionMessagingService.requestControl(member.getUuid().toString(), requester, controlAction));

        // As a safeguard, also trigger locally if the external broker relay is configured on this core node.
        if (targetMembers.stream().anyMatch(member -> member.getUuid().toString().equals(localMemberId))) {
            switch (controlAction) {
                case DISCONNECT -> websocketBrokerReconnectionService.triggerManualDisconnect();
                case CONNECT -> websocketBrokerReconnectionService.triggerManualConnect();
                default -> websocketBrokerReconnectionService.triggerManualReconnect();
            }
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
