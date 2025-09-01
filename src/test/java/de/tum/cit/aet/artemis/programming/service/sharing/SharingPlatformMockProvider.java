package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.RequestUtilService;

/**
 * Test utility class that provides infrastructure to mock the sharing platform
 * for testing purposes. This mock provider simulates connections between Artemis
 * and the sharing platform, allowing for isolated testing of sharing functionality.
 */
@Component
@ConditionalOnProperty(name = "artemis.sharing.enabled", havingValue = "true", matchIfMissing = false)
@Lazy
public class SharingPlatformMockProvider {

    /**
     * also needed by other test infrastructure.
     */
    public static final String TEST_INSTALLATION_NAME = "ArtemisTestInstance";

    public static final String SHARING_BASEURL = "http://localhost:9001/api";

    public static final String SHARING_BASEURL_PLUGIN = SHARING_BASEURL + "/pluginIF/v0.1";

    @Autowired
    private SharingConnectorService sharingConnectorService;

    private MockRestServiceServer mockSharingServer;

    @Autowired
    @Qualifier("sharingRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private RequestUtilService requestUtilService;

    public MockRestServiceServer getMockSharingServer() {
        return mockSharingServer;
    }

    public String getTestSharingApiKey() {
        return sharingApiKey;
    }

    /**
     * the shared secret api key
     */
    private static final String sharingApiKey = "someSecretlySharedKey1234";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Simulates a connection request from the Sharing Platform to Artemis.
     * This method sends a request to the Artemis sharing configuration endpoint with
     * predefined test parameters and validates the response.
     *
     * @return The SharingPluginConfig received from the Artemis endpoint
     * @throws Exception If the request fails or the response is invalid
     */
    public SharingPluginConfig connectRequestFromSharingPlatform() throws Exception {
        sharingConnectorService.setSharingApiKey(sharingApiKey);
        MvcResult result = requestUtilService
                .performMvcRequest(get("/api/core/sharing/config").queryParam("apiBaseUrl", SHARING_BASEURL_PLUGIN).queryParam("installationName", TEST_INSTALLATION_NAME)
                        .header("Authorization", "Bearer " + sharingApiKey).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        SharingPluginConfig sharingPluginConfig = objectMapper.readerFor(SharingPluginConfig.class).readValue(content);
        assertThat(sharingPluginConfig.pluginName).isEqualTo("Artemis Sharing Connector");

        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockSharingServer = builder.build();

        return sharingPluginConfig;
    }

    /**
     * Resets the mock servers
     */
    public void reset() throws Exception {
        sharingConnectorService.shutDown();
        if (mockSharingServer != null) {
            mockSharingServer.reset();
        }
    }

    /**
     * Mocks the sharing platform connection status.
     * When success is true, establishes a connection to simulate a successful platform state.
     * When success is false, resets the connection to simulate platform unavailability.
     *
     * @param success true to simulate successful connection, false to simulate failure/timeout
     * @throws Exception if the mock operation fails
     */
    public void mockStatus(boolean success) throws Exception {
        if (success) {
            connectRequestFromSharingPlatform();
        }
        else {
            reset();
        }
    }

    /**
     * returns the correct sharing info checksum for parameters
     *
     * @param sharingApiKey the api key
     * @param params        the parameters
     * @return the correct sharing info checksum
     */
    public static String calculateCorrectChecksum(String sharingApiKey, String... params) {
        Map<String, String> paramsToCheckSum = parseParamsToMap(params);
        return SecretChecksumCalculator.calculateChecksum(paramsToCheckSum, sharingApiKey);
    }

    /**
     * parses the parameter list, and returns them as a map.
     *
     * @param params the parameter list (alternating name and value)
     * @return a map of parameters
     */
    public static Map<String, String> parseParamsToMap(String... params) {
        if (params == null) {
            throw new IllegalArgumentException("params cannot be null");
        }
        Map<String, String> paramsMap = new HashMap<>();
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("params must contain an even number of elements (alternating name and value)");
        }
        for (int i = 0; i < params.length; i = i + 2) {
            String paramName = params[i];
            String paramValue = params[i + 1];
            if (paramName == null || paramValue == null) {
                throw new IllegalArgumentException("Parameter names and values cannot be null");
            }
            paramsMap.put(paramName, paramValue);
        }
        return paramsMap;
    }

}
