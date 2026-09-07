package de.tum.cit.aet.artemis.admin.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.admin.dto.WebsocketNodeDTO;
import de.tum.cit.aet.artemis.core.service.distributed.NodeRegistryService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * Covers the admin websocket node overview and the broker control endpoint.
 *
 * <p>
 * The node list is now built from {@link NodeRegistryService} rather than from the raw Hazelcast member list, so it only
 * reports core nodes. Build agents used to appear here as lite members even though the page hid every control for them.
 */
class AdminWebsocketIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "adminwebsocket";

    @Autowired
    private NodeRegistryService nodeRegistryService;

    @BeforeEach
    void setUp() {
        // Both the admin this class authenticates as and the student it expects to be rejected have to exist.
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        // The registry publishes lazily on a schedule; publish once so the endpoint has something to report.
        nodeRegistryService.heartbeat();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturnLiveCoreNodes() throws Exception {
        List<WebsocketNodeDTO> nodes = request.getList("/api/admin/websocket/nodes", HttpStatus.OK, WebsocketNodeDTO.class);

        assertThat(nodes).isNotEmpty();
        assertThat(nodes).allSatisfy(node -> {
            assertThat(node.memberId()).isNotBlank();
            assertThat(node.host()).isNotBlank();
        });
        assertThat(nodes).as("the node serving the request must be marked as local").anyMatch(WebsocketNodeDTO::local);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectNodeListForNonAdmin() throws Exception {
        request.getList("/api/admin/websocket/nodes", HttpStatus.FORBIDDEN, WebsocketNodeDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldAcceptReconnectForAllNodes() throws Exception {
        request.postWithoutResponseBody("/api/admin/websocket/reconnect", HttpStatus.ACCEPTED, new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldAcceptReconnectForSpecificNode() throws Exception {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("targetNodeId", nodeRegistryService.getLocalNodeId());

        request.postWithoutResponseBody("/api/admin/websocket/reconnect", HttpStatus.ACCEPTED, parameters);
    }

    /**
     * An unknown node matches nothing, which must be reported rather than silently accepted, otherwise an admin would
     * believe a reconnect was scheduled.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReportServiceUnavailableForUnknownNode() throws Exception {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("targetNodeId", "no-such-node");

        request.postWithoutResponseBody("/api/admin/websocket/reconnect", HttpStatus.SERVICE_UNAVAILABLE, parameters);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRejectUnknownControlAction() throws Exception {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("action", "NOT_AN_ACTION");

        request.postWithoutResponseBody("/api/admin/websocket/reconnect", HttpStatus.BAD_REQUEST, parameters);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectReconnectForNonAdmin() throws Exception {
        request.postWithoutResponseBody("/api/admin/websocket/reconnect", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
    }
}
