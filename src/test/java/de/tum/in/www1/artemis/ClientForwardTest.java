package de.tum.in.www1.artemis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.in.www1.artemis.web.rest.ClientForwardResource;
import de.tum.in.www1.artemis.web.rest.LogsResource;

/**
 * Test class for the ClientForwardController REST controller.
 *
 * @see ClientForwardResource
 */
public class ClientForwardTest extends AbstractSpringDevelopmentTest {

    private MockMvc restMockMvc;

    @BeforeEach
    public void setup() {
        var clientForwardController = new ClientForwardResource();
        var logsResource = new LogsResource();
        this.restMockMvc = MockMvcBuilders.standaloneSetup(clientForwardController, logsResource).build();
    }

    @Test
    public void getManagementEndpoint() throws Exception {
        restMockMvc.perform(get("/management/logs")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    public void getClientEndpoint() throws Exception {
        ResultActions perform = restMockMvc.perform(get("/non-existant-mapping"));
        perform.andExpect(status().isOk()).andExpect(forwardedUrl("/"));
    }

    @Test
    public void getNestedClientEndpoint() throws Exception {
        restMockMvc.perform(get("/admin/user-management")).andExpect(status().isOk()).andExpect(forwardedUrl("/"));
    }
}
