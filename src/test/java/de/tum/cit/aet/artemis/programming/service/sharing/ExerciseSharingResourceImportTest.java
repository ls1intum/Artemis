package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.core.web.SharingSupportResource;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
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
    private ExerciseSharingService exerciseSharingService;

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
        sharingPlatformMockProvider.reset(); // Mocks a disconnect frpm Sharing Plattform
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

        MvcResult result = requestUtilService
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
    @Disabled("End-to-end orchestration test is too broad; split into focused tests or enable when stabilized.")
    @WithMockUser(username = INSTRUCTOR_NAME, roles = "INSTRUCTOR")
    void importExerciseCompleteProcess() throws Exception {
        userUtilService.addInstructor("Sharing", INSTRUCTOR_NAME);
        setUpMockRestServer(SAMPLE_BASKET_TOKEN);

        String basketToken = importBasket();

        SharingInfoDTO sharingInfo = new SharingInfoDTO(basketToken, TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN,
                SharingPlatformMockProvider.calculateCorrectChecksum(sharingPlatformMockProvider.getTestSharingApiKey(), "returnURL", TEST_RETURN_URL, "apiBaseURL",
                        sharingConnectorService.getSharingApiBaseUrlOrNull().toString()),
                0);

        Course course1 = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        makeCourseJSONSerializable(course1);

        ProgrammingExercise exercise = getAndTestExerciseDetails(sharingInfo);

        SharingSetupInfo setupInfo = new SharingSetupInfo(exercise, course1, sharingInfo);

        // last step: do Exercise Import
        // mock gitService et al.
        doReturn(false).when(versionControlService).checkIfProjectExists(anyString(), anyString());
        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(anyString(), anyString()); // remark: null is returned anyway.

        doReturn("someBuildName").when(continuousIntegrationService).copyBuildPlan(any(), anyString(), any(), anyString(), anyString(), anyBoolean());

        String testURL = "https://unused.tum.de/git/" + exercise.getProjectKey() + "/" + exercise.getProjectKey() + ".git";
        doReturn(new LocalVCRepositoryUri(testURL)).when(versionControlService).getCloneRepositoryUri(eq(exercise.getProjectKey()), any());

        doAnswer(invocation -> {
            VcsRepositoryUri uri = invocation.getArgument(0, VcsRepositoryUri.class);
            Repository mockedRepository = Mockito.mock(Repository.class);
            Path tempDir = Files.createTempDirectory("sharingImportTest" + uri.hashCode());
            tempDir.toFile().deleteOnExit();
            doReturn(tempDir).when(mockedRepository).getLocalPath();
            return mockedRepository;
        }).when(gitService).getOrCheckoutRepository(any(), anyBoolean());

        doNothing().when(gitService).stageAllChanges(any());
        doNothing().when(gitService).commitAndPush(any(), anyString(), anyBoolean(), any());
        doReturn(List.of()).when(gitService).getFiles(any());
        doAnswer(invocation -> ObjectId.fromString("419a93c002688aeb7a5fd3badada7f263c1926ba")).when(gitService).getLastCommitHash(any());
        // doNothing().when(continuousIntegrationTriggerService).triggerBuild(any());

        String setupInfoJsonString = objectMapper.writeValueAsString(setupInfo);
        requestUtilService
                .performMvcRequest(
                        post("/api/programming/sharing/setup-import").contentType(MediaType.APPLICATION_JSON).content(setupInfoJsonString).accept(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        // finally, cleanup the cache
        exerciseSharingService.getRepositoryCache().asMap().forEach((key, value) -> exerciseSharingService.getRepositoryCache().invalidate(key));
        exerciseSharingService.getRepositoryCache().cleanUp();
    }

    private void setUpMockRestServer(String basketToken) throws IOException, URISyntaxException {
        byte[] zippedBytes;
        try (var is = Objects.requireNonNull(getClass().getResource("./basket/sampleExercise.zip")).openStream()) {
            zippedBytes = IOUtils.toByteArray(is);
        }
        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + basketToken + "/repository/0?format=artemis");

        final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(basketURI))
                .andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withSuccess(zippedBytes, MediaType.APPLICATION_OCTET_STREAM));
    }

    private ProgrammingExercise getAndTestExerciseDetails(SharingInfoDTO sharingInfo) throws Exception {
        // get Exercise Details

        MvcResult resultED = requestUtilService
                .performMvcRequest(post("/api/programming/sharing/import/basket/exercise-details").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sharingInfo)).accept(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String contentED = resultED.getResponse().getContentAsString();

        ProgrammingExercise exercise = objectMapper.readerFor(ProgrammingExercise.class).readValue(contentED);
        assertThat(exercise.getTitle()).isEqualTo("JUnit IO Tests");
        assertThat(exercise.getProgrammingLanguage()).isEqualTo(ProgrammingLanguage.JAVA);

        return exercise;
    }

    /**
     * utility method to make the course returned by `programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases()` serializable.
     */
    private static void makeCourseJSONSerializable(Course course1) {
        course1.setCompetencies(Set.of());
        course1.setLearningPaths(Set.of());
        course1.setTutorialGroups(Set.of());
        course1.setExams(Set.of());
        course1.setOrganizations(Set.of());
        course1.setPrerequisites(Set.of());
        course1.setFaqs(Set.of());

        course1.getExercises().forEach(e -> {
            e.setCompetencyLinks(null);
            e.setCategories(Set.of());
            e.setTeams(Set.of());
            e.setGradingCriteria(Set.of());
            e.setStudentParticipations(Set.of());
            e.setTutorParticipations(Set.of());
            e.setExampleSubmissions(Set.of());
            e.setAttachments(Set.of());
            e.setPlagiarismCases(Set.of());
            if (e instanceof ProgrammingExercise pe) {
                pe.setAuxiliaryRepositories(List.of());
                pe.setTemplateParticipation(null);
                pe.setSolutionParticipation(null);
                pe.setTestCases(Set.of());
                pe.setTasks(List.of());
                pe.setStaticCodeAnalysisCategories(Set.of());
                pe.setBuildConfig(null);
            }
        }); // just to make it json serializable
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
