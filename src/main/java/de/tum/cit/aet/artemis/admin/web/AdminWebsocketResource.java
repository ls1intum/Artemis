package de.tum.cit.aet.artemis.admin.web;

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

import de.tum.cit.aet.artemis.admin.dto.WebsocketNodeDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.distributed.NodeRegistryService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.messaging.WebsocketBrokerReconnectionMessagingService;
import de.tum.cit.aet.artemis.core.service.messaging.WebsocketBrokerReconnectionService;

@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage("monitoring/websocket-broker")
@RestController
@SuppressWarnings("deprecation")
@RequestMapping("api/admin/websocket/")
public class AdminWebsocketResource {

    private static final Logger log = LoggerFactory.getLogger(AdminWebsocketResource.class);

    private final WebsocketBrokerReconnectionService websocketBrokerReconnectionService;

    private final WebsocketBrokerReconnectionMessagingService websocketBrokerReconnectionMessagingService;

    private final DistributedDataProvider distributedDataProvider;

    private final NodeRegistryService nodeRegistryService;

    public AdminWebsocketResource(WebsocketBrokerReconnectionService websocketBrokerReconnectionService,
            WebsocketBrokerReconnectionMessagingService websocketBrokerReconnectionMessagingService, DistributedDataProvider distributedDataProvider,
            NodeRegistryService nodeRegistryService) {
        this.websocketBrokerReconnectionService = websocketBrokerReconnectionService;
        this.websocketBrokerReconnectionMessagingService = websocketBrokerReconnectionMessagingService;
        this.distributedDataProvider = distributedDataProvider;
        this.nodeRegistryService = nodeRegistryService;
    }

    /**
     * GET core/admin/websocket/nodes: returns the live core nodes (id and address).
     *
     * @return list of websocket nodes with metadata used by the admin UI
     */
    @GetMapping("nodes")
    public ResponseEntity<Iterable<WebsocketNodeDTO>> getWebsocketNodes() {
        String localId = nodeRegistryService.getLocalNodeId();
        var brokerStatus = distributedDataProvider.<String, Boolean>getMap(WebsocketBrokerReconnectionService.WEBSOCKET_BROKER_STATUS_MAP);
        // Only core nodes register with the node registry, so every entry here already serves websocket traffic. The
        // previously reported liteMember flag existed purely to filter Hazelcast build-agent members out of the raw member
        // list and no longer has a meaning, so it is gone from the response.
        var nodes = nodeRegistryService.getLiveNodes().stream().map(node -> new WebsocketNodeDTO(node.nodeId(), node.address(), node.host(), node.port(),
                node.nodeId().equals(localId), node.instanceId(), Boolean.TRUE.equals(brokerStatus.get(node.nodeId())))).toList();
        return ResponseEntity.ok(nodes);
    }

    /**
     * POST core/admin/websocket/reconnect: manually trigger reconnect attempts to the external websocket broker.
     *
     * @param targetNodeId optional cluster node id. If omitted, all nodes will reconnect.
     * @param action       desired control action (RECONNECT, DISCONNECT, CONNECT)
     * @return 202 (Accepted) if reconnect attempts were scheduled, 503 (Service Unavailable) otherwise
     */
    @PostMapping("reconnect")
    public ResponseEntity<Void> triggerReconnect(@RequestParam(value = "targetNodeId", required = false) String targetNodeId,
            @RequestParam(value = "action", required = false, defaultValue = "RECONNECT") String action) {
        String requester = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        log.info("REST request to trigger websocket broker action {} for target {} by {}", action, targetNodeId, requester);

        String localMemberId = nodeRegistryService.getLocalNodeId();
        var targetMembers = nodeRegistryService.getLiveNodes().stream().filter(node -> targetNodeId == null || targetNodeId.isBlank() || node.nodeId().equals(targetNodeId))
                .toList();

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

        targetMembers.forEach(node -> websocketBrokerReconnectionMessagingService.requestControl(node.nodeId(), requester, controlAction));

        // As a safeguard, also trigger locally if the external broker relay is configured on this core node.
        if (targetMembers.stream().anyMatch(node -> node.nodeId().equals(localMemberId))) {
            switch (controlAction) {
                case DISCONNECT -> websocketBrokerReconnectionService.triggerManualDisconnect();
                case CONNECT -> websocketBrokerReconnectionService.triggerManualConnect();
                default -> websocketBrokerReconnectionService.triggerManualReconnect();
            }
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
