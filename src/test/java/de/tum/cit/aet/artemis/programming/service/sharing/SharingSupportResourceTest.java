package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * this class tests all import features of the ExerciseSharingResource class
 */
class SharingSupportResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private RequestUtilService requestUtilService;

    @Autowired
    private SharingConnectorService sharingConnectorService;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected UserUtilService userUtilService;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlatform();
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
    }

    @Test
    void shouldReturnConfigurationWhenValidApiKeyProvided() throws Exception {
        MvcResult result = requestUtilService
                .performMvcRequest(get("/api/core/sharing/config").queryParam("apiBaseUrl", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN)
                        .queryParam("installationName", SharingPlatformMockProvider.TEST_INSTALLATION_NAME)
                        .header("Authorization", sharingPlatformMockProvider.getTestSharingApiKey()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        assertThat(content).isNotEmpty();
    }

    @Test
    void shouldReturnUnauthorizedWhenInvalidApiKeyProvided() throws Exception {
        requestUtilService
                .performMvcRequest(get("/api/core/sharing/config").queryParam("apiBaseUrl", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN)
                        .queryParam("installationName", SharingPlatformMockProvider.TEST_INSTALLATION_NAME).header("Authorization", "wrongKey"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnBadRequestWhenSuspiciousApiBaseURLWithMissingHost() throws Exception {
        requestUtilService.performMvcRequest(get("/api/core/sharing/config").queryParam("apiBaseUrl", "https:///missingHost/api")
                .queryParam("installationName", SharingPlatformMockProvider.TEST_INSTALLATION_NAME).header("Authorization", sharingPlatformMockProvider.getTestSharingApiKey())
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenSuspiciousApiBaseURLWithWeirdProtocoll() throws Exception {
        requestUtilService.performMvcRequest(get("/api/core/sharing/config").queryParam("apiBaseUrl", "ftp://localhost:1234/missingHost/api")
                .queryParam("installationName", SharingPlatformMockProvider.TEST_INSTALLATION_NAME).header("Authorization", sharingPlatformMockProvider.getTestSharingApiKey())
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    /**
     * request config with a missing api key
     *
     */
    @Test
    void shouldReturnUnauthorizedWhenApiKeyMissing() throws Exception {
        requestUtilService
                .performMvcRequest(get("/api/core/sharing/config").queryParam("apiBaseUrl", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN)
                        .queryParam("installationName", SharingPlatformMockProvider.TEST_INSTALLATION_NAME).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    /**
     * request config with misconfigured api base url
     *
     */
    @Test
    void connectRequestFromSharingPlatformWrongBaseURL() throws Exception {
        requestUtilService
                .performMvcRequest(get("/api/core/sharing/config").queryParam("apiBaseUrl", "invalid://missing.host/malformed/path")
                        .queryParam("installationName", SharingPlatformMockProvider.TEST_INSTALLATION_NAME)
                        .header("Authorization", "Bearer " + sharingPlatformMockProvider.getTestSharingApiKey()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    /**
     * request config with missing installation name
     *
     */
    @Test
    void connectRequestFromSharingPlatformWithMissingInstallationName() throws Exception {
        requestUtilService
                .performMvcRequest(get("/api/core/sharing/config").queryParam("apiBaseUrl", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN)
                        .header("Authorization", "Bearer " + sharingPlatformMockProvider.getTestSharingApiKey()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        assertThat(sharingConnectorService.getInstallationName()).isIn(SharingConnectorService.UNKNOWN_INSTALLATION_NAME, InetAddress.getLocalHost().getCanonicalHostName());
    }

}
