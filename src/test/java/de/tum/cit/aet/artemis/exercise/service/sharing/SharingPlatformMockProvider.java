package de.tum.cit.aet.artemis.exercise.service.sharing;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SHARING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

/**
 * provides infrastructure to set up and shutdown
 */
@Component
@Profile(PROFILE_SHARING)
public class SharingPlatformMockProvider {

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

    public SharingPluginConfig connectRequestFromSharingPlattform() throws Exception {
        MvcResult result = restMockMvc
                .perform(get("/api/sharing/config").queryParam("apiBaseUrl", "http://mocked").queryParam("installationName", "ArtemisTestInstance")
                        .header("Authorization", sharingApiKey).contentType(MediaType.APPLICATION_JSON))
                .andDo(print()).andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        SharingPluginConfig sharingPluginConfig = objectMapper.readerFor(SharingPluginConfig.class).readValue(content);
        assertThat(sharingPluginConfig.pluginName).isEqualTo("Artemis Sharing Connector");
        return sharingPluginConfig;
    }

    /**
     * Resets the mock servers
     */
    public void reset() throws Exception {
    }

    /**
     * registers or shuts down the required
     *
     * @param success Successful response or timeout.
     */
    public void mockStatus(boolean success) throws Exception {
        if (success) {
            connectRequestFromSharingPlattform();
        }
        else {
            sharingConnectorService.shutDown();
        }
    }

}
