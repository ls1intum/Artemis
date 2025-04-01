package de.tum.cit.aet.artemis.exercise.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * this class tests all import features of the ExerciseSharingResource class
 */
class ExerciseSharingResourceImportTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseSharingImportTests";

    public static final String SAMPLE_BASKET_TOKEN = "sampleBasketToken.json";

    public static final String TEST_RETURN_URL = "http://testing/xyz1";

    @Value("${artemis.sharing.apikey:#{null}}")
    private String sharingApiKey;

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private SharingConnectorService sharingConnectorService;

    @Autowired
    @Qualifier("sharingRestTemplate")
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlattform();
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
    }

    @Test
    void isSharingPlatformUp() throws Exception {
        MvcResult result = restMockMvc.perform(get("/api" + Constants.SHARINGCONFIG_RESOURCE_IS_ENABLED).contentType(MediaType.APPLICATION_JSON))
                // .andDo(print())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        assertThat(content).isEqualTo("true");
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * tests the import of a basket from the sharing platform. This test is also reused for priming of other tests
     *
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportBasket() throws Exception {
        importBasket();
    }

    private String importBasket() throws Exception {
        String sampleBasket = IOUtils.toString(Objects.requireNonNull(this.getClass().getResource("./basket/sampleBasket.json")), StandardCharsets.UTF_8);

        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + SAMPLE_BASKET_TOKEN);
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockServer = builder.build();

        final ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(basketURI)).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withSuccess(sampleBasket, MediaType.APPLICATION_JSON));

        MvcResult result = restMockMvc
                .perform(addCorrectChecksum(get("/api/sharing/import/basket").queryParam("basketToken", SAMPLE_BASKET_TOKEN), "returnURL", TEST_RETURN_URL, "apiBaseURL",
                        SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN).contentType(MediaType.APPLICATION_JSON))
                /* .andDo(print()) */
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        ShoppingBasket sb = objectMapper.readerFor(ShoppingBasket.class).readValue(content);
        assertThat(sb.userInfo.email).isEqualTo("michael.breu@uibk.ac.at");
        return SAMPLE_BASKET_TOKEN;
    }

    /**
     * tests the import of a basket from the sharing platform
     *
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importBasketNotFound() throws Exception {

        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/undefinedBasket.json");
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockServer = builder.build();

        final ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(basketURI)).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withResourceNotFound());

        restMockMvc.perform(addCorrectChecksum(get("/api/sharing/import/basket").queryParam("basketToken", "undefinedBasket.json"), "returnURL", "http://testing/xyz1",
                "apiBaseURL", sharingConnectorService.getSharingApiBaseUrlOrNull().toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder addCorrectChecksum(MockHttpServletRequestBuilder request, String... params) {
        Map<String, String> paramsToCheckSum = new HashMap<>();
        assertThat(params.length % 2).isEqualTo(0).describedAs("paramList must contain even elements");

        for (int i = 0; i < params.length; i = i + 2) {
            String paramName = params[i];
            String paramValue = params[i + 1];
            paramsToCheckSum.put(paramName, paramValue);
            request.queryParam(paramName, paramValue);
        }
        String checkSum = SecretChecksumCalculator.calculateChecksum(paramsToCheckSum, sharingApiKey);
        request.queryParam("checksum", checkSum);
        return request;
    }

    private String calculateCorrectChecksum(String... params) {
        Map<String, String> paramsToCheckSum = new HashMap<>();
        assertThat(params.length % 2).isEqualTo(0).describedAs("paramList must contain even elements");
        for (int i = 0; i < params.length; i = i + 2) {
            String paramName = params[i];
            String paramValue = params[i + 1];
            paramsToCheckSum.put(paramName, paramValue);
        }
        return SecretChecksumCalculator.calculateChecksum(paramsToCheckSum, sharingApiKey);
    }

    /**
     * tests the import of a basket from the sharing platform
     *
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importBasketWrongChecksum() throws Exception {
        restMockMvc.perform(get("/api/sharing/import/basket").queryParam("basketToken", "sampleBasket.json").queryParam("returnURL", "http://testing/xyz1")
                .queryParam("apiBaseURL", sharingConnectorService.getSharingApiBaseUrlOrNull().toString()).queryParam("checksum", "wrongChecksum")
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseInfos() throws Exception {

        String basketToken = importBasket();

        byte[] zippedBytes = IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResource("./basket/sampleExercise.zip")).openStream());
        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + basketToken + "/repository/0?format=artemis");
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockServer = builder.build();

        final ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(basketURI)).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withSuccess(zippedBytes, MediaType.APPLICATION_OCTET_STREAM));

        SharingInfoDTO sharingInfo = new SharingInfoDTO();
        sharingInfo.setApiBaseURL(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN);
        sharingInfo.setReturnURL(TEST_RETURN_URL);
        sharingInfo.setBasketToken(basketToken);
        sharingInfo.setChecksum(calculateCorrectChecksum("returnURL", TEST_RETURN_URL, "apiBaseURL", sharingConnectorService.getSharingApiBaseUrlOrNull().toString()));

        MvcResult result = restMockMvc.perform(post("/api/sharing/import/basket/exerciseDetails").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sharingInfo)).accept(MediaType.APPLICATION_JSON)).andDo(print())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        ObjectMapper programmingExerciseObjectMapper = new ObjectMapper();
        programmingExerciseObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        programmingExerciseObjectMapper.findAndRegisterModules();

        ProgrammingExercise exercise = programmingExerciseObjectMapper.readerFor(ProgrammingExercise.class).readValue(content);
        assertThat(exercise.getTitle()).isEqualTo("JUnit IO Tests");
        assertThat(exercise.getProgrammingLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseInfosWrongChecksum() throws Exception {

        SharingInfoDTO sharingInfo = new SharingInfoDTO();
        sharingInfo.setApiBaseURL(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN);
        sharingInfo.setReturnURL(TEST_RETURN_URL);
        sharingInfo.setBasketToken("Some Basket Token");
        sharingInfo.setChecksum("Invalid Checksum");

        restMockMvc.perform(post("/api/sharing/import/basket/exerciseDetails").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(sharingInfo))
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

}
