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

import de.tum.cit.aet.artemis.core.config.websocket.WebsocketBrokerReconnectionService;
import de.tum.cit.aet.artemis.core.dto.WebsocketNodeDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.messaging.WebsocketBrokerReconnectionMessagingService;

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
     */
    @GetMapping("nodes")
    public ResponseEntity<Iterable<WebsocketNodeDTO>> getWebsocketNodes() {
        var cluster = hazelcastInstance.getCluster();
        var localId = cluster.getLocalMember().getUuid().toString();
        var nodes = cluster.getMembers().stream().map(member -> new WebsocketNodeDTO(member.getUuid().toString(), member.getAddress().toString(), member.getAddress().getHost(),
                member.getAddress().getPort(), member.getUuid().toString().equals(localId))).toList();
        return ResponseEntity.ok(nodes);
    }

    /**
     * POST core/admin/websocket/reconnect: manually trigger reconnect attempts to the external websocket broker.
     *
     * @param targetNodeId optional hazelcast member id. If omitted, all nodes will reconnect.
     * @return 202 (Accepted) if reconnect attempts were scheduled, 503 (Service Unavailable) otherwise
     */
    @PostMapping("reconnect")
    public ResponseEntity<Void> triggerReconnect(@RequestParam(value = "targetNodeId", required = false) String targetNodeId) {
        String requester = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        String target = targetNodeId == null || targetNodeId.isBlank() ? "all" : targetNodeId;
        log.info("REST request to trigger websocket broker reconnect for target {} by {}", target, requester);

        websocketBrokerReconnectionMessagingService.requestReconnect(target, requester);

        // As a safeguard, also trigger locally if the external broker relay is configured here.
        websocketBrokerReconnectionService.triggerManualReconnect();
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
