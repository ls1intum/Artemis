package de.tum.cit.aet.artemis.programming.service.sharing;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SHARING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * provides infrastructure to set up and shutdown
 */
@Component
@Profile(PROFILE_SHARING)
public class SharingPlatformMockProvider {

    protected static final String TEST_INSTALLATION_NAME = "ArtemisTestInstance";

    public static final String SHARING_BASEURL = "http://localhost:9001/api";

    public static final String SHARING_BASEURL_PLUGIN = SHARING_BASEURL + "/pluginIF/v0.1";

    /**
     * the shared secret api key
     */
    @Value("${artemis.sharing.apikey:#{null}}")
    private String sharingApiKey;

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    SharingConnectorService sharingConnectorService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SharingPlatformMockProvider() {
    }

    /**
     * Simulates a connection request from the Sharing Platform to Artemis.
     * This method sends a request to the Artemis sharing configuration endpoint with
     * predefined test parameters and validates the response.
     *
     * @return The SharingPluginConfig received from the Artemis endpoint
     * @throws Exception If the request fails or the response is invalid
     */
    public SharingPluginConfig connectRequestFromSharingPlatform() throws Exception {
        MvcResult result = restMockMvc
                .perform(get("/api/core/sharing/config").queryParam("apiBaseUrl", SHARING_BASEURL_PLUGIN).queryParam("installationName", TEST_INSTALLATION_NAME)
                        .header("Authorization", sharingApiKey).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        SharingPluginConfig sharingPluginConfig = objectMapper.readerFor(SharingPluginConfig.class).readValue(content);
        assertThat(sharingPluginConfig.pluginName).isEqualTo("Artemis Sharing Connector");
        return sharingPluginConfig;
    }

    /**
     * Resets the mock servers
     */
    public void reset() throws Exception {
        sharingConnectorService.shutDown();
    }

    /**
     * registers or shuts down the required
     *
     * @param success Successful response or timeout.
     */
    public void mockStatus(boolean success) throws Exception {
        if (success) {
            connectRequestFromSharingPlatform();
        }
        else {
            reset();
        }
    }

}
