package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.core.web.SharingSupportResource;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO;
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

    @Autowired
    private CompetencyExerciseLinkTestRepository competencyExerciseLinkTestRepository;

    @BeforeEach
    void startUp() throws Exception {
        log.info("Mocking connect from Sharing Platform");
        sharingPlatformMockProvider.connectRequestFromSharingPlatform();
    }

    private ObjectMapper objectMapper;

    @BeforeEach
    void setupObjectMapper() {
        objectMapper = JsonObjectMapper.get().copy();
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

        SharingSetupInfoDTO emptySetupInfo = new SharingSetupInfoDTO(null, 0, correctSharingInfo());

        // last step: do Exercise Import
        requestUtilService.performMvcRequest(post("/api/programming/sharing/setup-import").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptySetupInfo)).accept(MediaType.APPLICATION_JSON)).andExpect(status().is5xxServerError());

    }

    /**
     * The whole import is driven by the basket reference. Without it the service dereferenced null and the caller saw
     * a 500; a malformed request has to be rejected at the boundary instead.
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void setUpWithMissingSharingInfoIsRejected() throws Exception {
        SharingSetupInfoDTO setupInfoWithoutSharingInfo = new SharingSetupInfoDTO(null, 0, null);

        requestUtilService
                .performMvcRequest(post("/api/programming/sharing/setup-import").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setupInfoWithoutSharingInfo)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorKey").value("sharingInfoMissing"));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void importExerciseInfosWrongChecksum() throws Exception {

        SharingInfoDTO sharingInfo = new SharingInfoDTO("Some Basket Token", TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN, "Invalid Checksum", 0);

        requestUtilService.performMvcRequest(post("/api/programming/sharing/import/basket/exercise-details").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sharingInfo)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    /**
     * Golden path of the exercise-details call: the returned object backs the whole create form in the client and is
     * posted back to setup-import unchanged, so every field that form binds has to survive the round trip.
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void shouldReturnExerciseDetailsFromBasket() throws Exception {
        mockSampleBasketZipForToken(SAMPLE_BASKET_TOKEN, ExpectedCount.once());

        ImportProgrammingExerciseRequestDTO exerciseDetails = loadExerciseDetails();

        // The exported id belongs to the source instance: the client must create a new exercise, not update one.
        assertThat(exerciseDetails.id()).isNull();
        assertThat(exerciseDetails.title()).isNotBlank();
        assertThat(exerciseDetails.shortName()).isNotBlank();
        assertThat(exerciseDetails.programmingLanguage()).isNotNull();
        assertThat(exerciseDetails.projectKey()).isNotBlank();
        assertThat(exerciseDetails.buildConfig()).isNotNull();
        assertThat(exerciseDetails.maxPoints()).isNotNull();
    }

    /**
     * Golden path of the sharing import: the details object is posted back exactly as the client sends it, and the
     * response has to carry what the client needs to navigate to the created exercise.
     * <p>
     * The create form also lets the author pick competencies of the target course, and the request record cannot bind
     * those links itself (they need managed competencies), so the import has to resolve them; without that the exercise
     * would be created with no links at all and nothing would fail loudly.
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void shouldImportExerciseFromSharingPlatform() throws Exception {
        // Expectations have to be registered before the first request: the details call and the import each fetch the
        // basket item once.
        mockSampleBasketZipForToken(SAMPLE_BASKET_TOKEN, ExpectedCount.twice());
        ImportProgrammingExerciseRequestDTO exerciseDetails = loadExerciseDetails();

        Course course = courseUtilService.addEmptyCourse();
        userUtilService.addInstructorToCourse(INSTRUCTOR_NAME, course);
        Competency competency = competencyUtilService.createCompetency(course);
        SharingSetupInfoDTO setupInfo = new SharingSetupInfoDTO(exerciseDetails, course.getId(), correctSharingInfo());

        ProgrammingExercise savedExercise = null;
        try {
            MvcResult result = requestUtilService.performMvcRequest(post("/api/programming/sharing/setup-import").contentType(MediaType.APPLICATION_JSON)
                    .content(withCompetencyLink(setupInfo, competency.getId())).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(response.get("id").asLong()).isPositive();
            assertThat(response.get("type").asText()).isEqualTo("programming");
            assertThat(response.get("title").asText()).isEqualTo(exerciseDetails.title());
            // The nested course drives the follow-up navigation, so it must not be flattened to an id.
            assertThat(response.get("course").get("id").asLong()).isEqualTo(course.getId());
            assertThat(response.get("course").get("title").asText()).isEqualTo(course.getTitle());

            long importedExerciseId = response.get("id").asLong();
            savedExercise = programmingExerciseRepository.findByIdElseThrow(importedExerciseId);
            assertThat(savedExercise.getTitle()).isEqualTo(exerciseDetails.title());
            assertThat(savedExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course.getId());

            // read the rows back, not the in-memory graph: the links are persisted only after the exercise has an id
            List<CompetencyExerciseLink> storedLinks = competencyExerciseLinkTestRepository.findByExerciseIdWithCompetency(importedExerciseId);
            assertThat(storedLinks).hasSize(1);
            assertThat(storedLinks.getFirst().getCompetency().getId()).isEqualTo(competency.getId());
            assertThat(storedLinks.getFirst().getWeight()).isEqualTo(1);
        }
        finally {
            // The repositories have to be removed explicitly, otherwise a repeated run fails because they already exist.
            // The project key is derived from the target course, not taken from the exported details.
            versionControlService.deleteProject(savedExercise != null ? savedExercise.getProjectKey() : exerciseDetails.projectKey());
        }
    }

    /**
     * Serializes the setup info the way the client does and adds the one competency link the author picked in the
     * create form. Building it on the JSON keeps the test off the 40-odd components of the exercise record.
     *
     * @param setupInfo    the setup info to post
     * @param competencyId the id of the competency to link
     * @return the request body
     */
    private String withCompetencyLink(SharingSetupInfoDTO setupInfo, long competencyId) throws Exception {
        ObjectNode body = objectMapper.valueToTree(setupInfo);
        ObjectNode link = objectMapper.createObjectNode();
        link.putObject("competency").put("id", competencyId);
        link.put("weight", 1);
        ((ObjectNode) body.get("exercise")).set("competencyLinks", objectMapper.createArrayNode().add(link));
        return objectMapper.writeValueAsString(body);
    }

    private SharingInfoDTO correctSharingInfo() {
        return new SharingInfoDTO(SAMPLE_BASKET_TOKEN, TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN, SharingPlatformMockProvider.calculateCorrectChecksum(
                sharingPlatformMockProvider.getTestSharingApiKey(), "returnURL", TEST_RETURN_URL, "apiBaseURL", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN), 0);
    }

    private ImportProgrammingExerciseRequestDTO loadExerciseDetails() throws Exception {
        MvcResult result = requestUtilService.performMvcRequest(post("/api/programming/sharing/import/basket/exercise-details").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(correctSharingInfo())).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), ImportProgrammingExerciseRequestDTO.class);
    }

    private void mockSampleBasketZipForToken(String basketToken, ExpectedCount expectedCount) throws Exception {
        URI basketRepositoryZipURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + basketToken + "/repository/0?format=artemis");
        try (InputStream inputStream = Objects.requireNonNull(getClass().getResource("./basket/sampleExercise.zip")).openStream()) {
            byte[] zippedBytes = inputStream.readAllBytes();
            final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(expectedCount, requestTo(basketRepositoryZipURI))
                    .andExpect(method(HttpMethod.GET));
            responseActions.andRespond(MockRestResponseCreators.withSuccess(zippedBytes, MediaType.APPLICATION_OCTET_STREAM));
        }
    }

    private void importBasket() throws Exception {
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
    }

}
