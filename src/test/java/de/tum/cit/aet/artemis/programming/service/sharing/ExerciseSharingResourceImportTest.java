package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.core.web.SharingSupportResource;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * this class tests all import features of the ExerciseSharingResource class
 */
class ExerciseSharingResourceImportTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "exercisesharingimporttests";

    private static final String INSTRUCTOR_NAME = TEST_PREFIX + "instructor1";

    private static final String SAMPLE_BASKET_TOKEN = "sampleBasketToken";

    private static final String TEST_RETURN_URL = "http://testing/xyz1";

    private static final Logger log = LoggerFactory.getLogger(ExerciseSharingResourceImportTest.class);

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
        log.info("Mocking connect from Sharing Platform");
        sharingPlatformMockProvider.connectRequestFromSharingPlatform();
    }

    private ObjectMapper objectMapper;

    @BeforeEach
    void setupObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
    }

    @Test
    void shouldReturnTrueWhenSharingPlatformIsEnabled() throws Exception {
        MvcResult result = requestUtilService
                .performMvcRequest(get("/api/core/sharing/" + SharingSupportResource.SHARINGCONFIG_RESOURCE_IS_ENABLED).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        Boolean answer = objectMapper.readerFor(Boolean.class).readValue(content);

        assertThat(answer).isTrue();
    }

    @Test
    void shouldReturnFalseWhenSharingPlatformIsNotYetConnected() throws Exception {
        sharingPlatformMockProvider.reset(); // Mocks a disconnect from Sharing Platform
        MvcResult result = requestUtilService
                .performMvcRequest(get("/api/core/sharing/" + SharingSupportResource.SHARINGCONFIG_RESOURCE_IS_ENABLED).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        Boolean answer = objectMapper.readerFor(Boolean.class).readValue(content);

        assertThat(answer).isFalse();
    }

    /**
     * Tests the import of a basket from the sharing platform. This test is also reused for priming of other tests
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void shouldSuccessfullyImportBasketFromSharingPlatform() throws Exception {
        importBasket();
    }

    /**
     * Tests the import of a basket from the sharing platform. This test is also reused for priming of other tests
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "EDITOR")
    void shouldSuccessfullyImportBasketFromSharingPlatformAsEditor() throws Exception {
        importBasket();
    }

    /**
     * Tests the import of a basket from the sharing platform. This test is also reused for priming of other tests
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "USER")
    void shouldSuccessfullyImportBasketFromSharingPlatformAsStudentNotAuthorized() throws Exception {
        String sampleBasket = IOUtils.toString(Objects.requireNonNull(this.getClass().getResource("./basket/sampleBasket.json")), StandardCharsets.UTF_8);

        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + SAMPLE_BASKET_TOKEN);

        final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketURI))
                .andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withSuccess(sampleBasket, MediaType.APPLICATION_JSON));

        requestUtilService
                .performMvcRequest(addCorrectChecksum(get("/api/programming/sharing/import/basket").queryParam("basketToken", SAMPLE_BASKET_TOKEN), "returnURL", TEST_RETURN_URL,
                        "apiBaseURL", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(status().is4xxClientError()).andReturn();
    }

    /**
     * tests the import of a basket from the sharing platform. This test is also reused for priming of other tests
     *
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void shouldReturnNotFoundWhenBasketImportFails() throws Exception {
        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + SAMPLE_BASKET_TOKEN);

        final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketURI))
                .andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withBadRequest());

        requestUtilService
                .performMvcRequest(addCorrectChecksum(get("/api/programming/sharing/import/basket").queryParam("basketToken", SAMPLE_BASKET_TOKEN), "returnURL", TEST_RETURN_URL,
                        "apiBaseURL", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(status().isNotFound());
    }

    /**
     * tests the import of a basket from the sharing platform
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void shouldReturnNotFoundWhenBasketDoesNotExist() throws Exception {

        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/undefinedBasketToken");

        final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketURI))
                .andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withResourceNotFound());

        requestUtilService
                .performMvcRequest(addCorrectChecksum(get("/api/programming/sharing/import/basket").queryParam("basketToken", "undefinedBasketToken"), "returnURL",
                        "http://testing/xyz1", "apiBaseURL", sharingConnectorService.getSharingApiBaseUrlOrNull().toString()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder addCorrectChecksum(MockHttpServletRequestBuilder request, String... params) {
        Map<String, String> paramsToCheckSum = SharingPlatformMockProvider.parseParamsToMap(params);

        // Add params to request
        paramsToCheckSum.forEach(request::queryParam);

        String checkSum = SecretChecksumCalculator.calculateChecksum(paramsToCheckSum, sharingPlatformMockProvider.getTestSharingApiKey());
        request.queryParam("checksum", checkSum);
        return request;
    }

    /**
     * tests the import of a basket from the sharing platform
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void shouldReturnBadRequestWhenChecksumIsInvalid() throws Exception {
        requestUtilService.performMvcRequest(get("/api/programming/sharing/import/basket").queryParam("basketToken", "sampleBasket.json")
                .queryParam("returnURL", "http://testing/xyz1").queryParam("apiBaseURL", sharingConnectorService.getSharingApiBaseUrlOrNull().toString())
                .queryParam("checksum", "wrongChecksum").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void setUpWithMissingExercise() throws Exception {

        SharingSetupInfo emptySetupInfo = new SharingSetupInfo(null, null, null);

        // last step: do Exercise Import
        requestUtilService.performMvcRequest(post("/api/programming/sharing/setup-import").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptySetupInfo)).accept(MediaType.APPLICATION_JSON)).andExpect(status().is5xxServerError());

    }

    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void importExerciseInfosWrongChecksum() throws Exception {

        SharingInfoDTO sharingInfo = new SharingInfoDTO("Some Basket Token", TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN, "Invalid Checksum", 0);

        requestUtilService.performMvcRequest(post("/api/programming/sharing/import/basket/exercise-details").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sharingInfo)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    private String importBasket() throws Exception {
        String sampleBasket = IOUtils.toString(Objects.requireNonNull(this.getClass().getResource("./basket/sampleBasket.json")), StandardCharsets.UTF_8);

        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + SAMPLE_BASKET_TOKEN);

        final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketURI))
                .andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withSuccess(sampleBasket, MediaType.APPLICATION_JSON));

        MvcResult result = requestUtilService
                .performMvcRequest(addCorrectChecksum(get("/api/programming/sharing/import/basket").queryParam("basketToken", SAMPLE_BASKET_TOKEN), "returnURL", TEST_RETURN_URL,
                        "apiBaseURL", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        ShoppingBasket sb = objectMapper.readerFor(ShoppingBasket.class).readValue(content);
        assertThat(sb.userInfo.email).isEqualTo("test.user@example.com");
        return SAMPLE_BASKET_TOKEN;
    }

}
