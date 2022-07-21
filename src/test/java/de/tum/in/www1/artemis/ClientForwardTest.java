package de.tum.in.www1.artemis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import de.tum.in.www1.artemis.web.rest.ClientForwardResource;
import de.tum.in.www1.artemis.web.rest.vm.LoggerVM;

/**
 * Test class for the ClientForwardController REST controller.
 *
 * @see ClientForwardResource
 */
class ClientForwardTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testManagementEndpoint() throws Exception {
        request.getList("/management/logs", HttpStatus.OK, LoggerVM.class);
    }

    @Test
    void testClientEndpoint() throws Exception {
        ResultActions perform = request.getMvc().perform(get("/non-existent-mapping"));
        perform.andExpect(status().isOk()).andExpect(forwardedUrl("/"));
    }

    @Test
    void testNestedClientEndpoint() throws Exception {
        request.getMvc().perform(get("/admin/user-management")).andExpect(status().isOk()).andExpect(forwardedUrl("/"));
    }

    @Test
    void getUnmappedDottedEndpoint() throws Exception {
        request.getMvc().perform(get("/foo.js")).andExpect(status().isNotFound());
    }

    @Test
    void getUnmappedNestedDottedEndpoint() throws Exception {
        request.getMvc().perform(get("/foo/bar.js")).andExpect(status().isNotFound());
    }

    @Test
    void getWebsocketInfoEndpoint() throws Exception {
        request.getMvc().perform(get("/websocket/info")).andExpect(status().isNotFound());
    }

    @Test
    void getWebsocketEndpoint() throws Exception {
        request.getMvc().perform(get("/websocket/tracker/308/sessionId/websocket")).andExpect(status().isBadRequest());
    }

    @Test
    void getWebsocketFallbackEndpoint() throws Exception {
        request.getMvc().perform(get("/websocket/tracker/308/sessionId/xhr_streaming")).andExpect(status().isNotFound());
    }
}
