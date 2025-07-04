package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.core.web.SharingSupportResource;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * this class tests all import features of the ExerciseSharingResource class
 */
class ExerciseSharingResourceImportTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exercisesharingimporttests";

    public static final String INSTRUCTORNAME = TEST_PREFIX + "instructor1";

    public static final String SAMPLE_BASKET_TOKEN = "sampleBasketToken";

    public static final String TEST_RETURN_URL = "http://testing/xyz1";

    @Value("${artemis.sharing.apikey:#{null}}")
    private String sharingApiKey;

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private RequestUtilService requestUtilService;

    @Autowired
    private SharingConnectorService sharingConnectorService;

    // Util Services
    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    private ExerciseSharingService exerciseSharingService;

    @Autowired
    @Qualifier("sharingRestTemplate")
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlatform();
    }

    private ObjectMapper objectMapper;

    @BeforeEach
    void setupObjectMapper() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
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
        assertThat(content).isEqualTo("true");
    }

    /**
     * Tests the import of a basket from the sharing platform. This test is also reused for priming of other tests
     *
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldSuccessfullyImportBasketFromSharingPlatform() throws Exception {
        importBasket();
    }

    /**
     * tests the import of a basket from the sharing platform. This test is also reused for priming of other tests
     *
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnNotFoundWhenBasketImportFails() throws Exception {
        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + SAMPLE_BASKET_TOKEN);
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockServer = builder.build();

        final ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(basketURI)).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withBadRequest());

        requestUtilService
                .performMvcRequest(addCorrectChecksum(get("/api/programming/sharing/import/basket").queryParam("basketToken", SAMPLE_BASKET_TOKEN), "returnURL", TEST_RETURN_URL,
                        "apiBaseURL", SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(status().isNotFound());
    }

    /**
     * tests the import of a basket from the sharing platform
     *
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnNotFoundWhenBasketDoesNotExist() throws Exception {

        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/undefinedBasketToken");
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockServer = builder.build();

        final ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(basketURI)).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withResourceNotFound());

        requestUtilService
                .performMvcRequest(addCorrectChecksum(get("/api/programming/sharing/import/basket").queryParam("basketToken", "undefinedBasketToken"), "returnURL",
                        "http://testing/xyz1", "apiBaseURL", sharingConnectorService.getSharingApiBaseUrlOrNull().toString()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private Map<String, String> parseParamsToMap(String... params) {
        if (params == null) {
            throw new IllegalArgumentException("params cannot be null");
        }
        Map<String, String> paramsMap = new HashMap<>();
        assertThat(params.length % 2).isEqualTo(0).describedAs("paramList must contain even elements");
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

    private String calculateCorrectChecksum(String... params) {
        Map<String, String> paramsToCheckSum = parseParamsToMap(params);
        return SecretChecksumCalculator.calculateChecksum(paramsToCheckSum, sharingApiKey);
    }

    private MockHttpServletRequestBuilder addCorrectChecksum(MockHttpServletRequestBuilder request, String... params) {
        Map<String, String> paramsToCheckSum = parseParamsToMap(params);

        // Add params to request
        paramsToCheckSum.forEach(request::queryParam);

        String checkSum = SecretChecksumCalculator.calculateChecksum(paramsToCheckSum, sharingApiKey);
        request.queryParam("checksum", checkSum);
        return request;
    }

    /**
     * tests the import of a basket from the sharing platform
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnBadRequestWhenChecksumIsInvalid() throws Exception {
        requestUtilService.performMvcRequest(get("/api/programming/sharing/import/basket").queryParam("basketToken", "sampleBasket.json")
                .queryParam("returnURL", "http://testing/xyz1").queryParam("apiBaseURL", sharingConnectorService.getSharingApiBaseUrlOrNull().toString())
                .queryParam("checksum", "wrongChecksum").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = INSTRUCTORNAME + "1", roles = "INSTRUCTOR")
    void importExerciseCompleteProcess() throws Exception {
        userUtilService.addInstructor("Sharing", INSTRUCTORNAME); // unfortunately this utility extends the username by "1"

        String basketToken = importBasket();

        setUpMockRestServer(basketToken);

        SharingInfoDTO sharingInfo = new SharingInfoDTO(basketToken, TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN,
                calculateCorrectChecksum("returnURL", TEST_RETURN_URL, "apiBaseURL", sharingConnectorService.getSharingApiBaseUrlOrNull().toString()), 0);

        Course course1 = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();

        ProgrammingExercise exercise = getAndTestExerciseDetails(sharingInfo);

        validateProblemStatement(sharingInfo);

        SharingSetupInfo setupInfo = new SharingSetupInfo(exercise, course1, sharingInfo);

        // last step: do Exercise Import
        makeCourseJSONSerializable(course1);
        // just deactivate programmingLanguageFeatureService validation
        ProgrammingLanguageFeature trivialProgrammingLanguageFeatures = new ProgrammingLanguageFeature(exercise.getProgrammingLanguage(), true, false, false, true, false,
                List.of(ProjectType.PLAIN_MAVEN), false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(trivialProgrammingLanguageFeatures);
        // mock gitService et all.
        when(versionControlService.checkIfProjectExists(eq(exercise.getProjectKey()), eq(exercise.getProjectName()))).thenReturn(false);
        when(continuousIntegrationService.checkIfProjectExists(eq(exercise.getProjectKey()), eq(exercise.getProjectName()))).thenReturn(null);

        when(continuousIntegrationService.copyBuildPlan(any(), anyString(), any(), anyString(), anyString(), anyBoolean())).thenReturn("");

        when(versionControlService.getCloneRepositoryUri(eq(exercise.getProjectKey()), any())).thenReturn(new VcsRepositoryUri("http://some.cloneurl"));

        doAnswer(invocation -> {
            VcsRepositoryUri uri = invocation.getArgument(0, VcsRepositoryUri.class);
            Repository mockedRepository = Mockito.mock(Repository.class);
            Mockito.when(mockedRepository.getLocalPath()).thenReturn(Files.createTempDirectory("sharingImportTest" + uri.hashCode()));
            return mockedRepository;
        }).when(gitService).getOrCheckoutRepository(any(), anyBoolean());

        doNothing().when(gitService).stageAllChanges(any());
        doNothing().when(gitService).commitAndPush(any(), anyString(), anyBoolean(), any());
        doReturn(List.of()).when(gitService).getFiles(any());
        doAnswer(invocation -> ObjectId.fromString("419a93c002688aeb7a5fd3badada7f263c1926ba")).when(gitService).getLastCommitHash(any());
        // doNothing().when(continuousIntegrationTriggerService).triggerBuild(any());

        String setupInfoJsonString = objectMapper.writeValueAsString(setupInfo);
        MvcResult resultPE = requestUtilService
                .performMvcRequest(
                        post("/api/programming/sharing/setup-import").contentType(MediaType.APPLICATION_JSON).content(setupInfoJsonString).accept(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        // finally cleanup the cache
        exerciseSharingService.getRepositoryCache().asMap().forEach((key, value) -> {
            exerciseSharingService.getRepositoryCache().invalidate(key);
        });
        exerciseSharingService.getRepositoryCache().cleanUp();
    }

    private void setUpMockRestServer(String basketToken) throws IOException, URISyntaxException {
        byte[] zippedBytes = IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResource("./basket/sampleExercise.zip")).openStream());
        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + basketToken + "/repository/0?format=artemis");
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockServer = builder.build();

        final ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(basketURI)).andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withSuccess(zippedBytes, MediaType.APPLICATION_OCTET_STREAM));
    }

    private void validateProblemStatement(SharingInfoDTO sharingInfo) throws Exception, JsonProcessingException, UnsupportedEncodingException {
        // get Problem Statement
        MvcResult resultPS = requestUtilService
                .performMvcRequest(post("/api/programming/sharing/import/basket/problem-statement").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sharingInfo)).accept(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN)).andExpect(status().isOk()).andReturn();
        // Zip file is cached, no extra request to sharing platform required!
        String contentPS = resultPS.getResponse().getContentAsString();
        assertThat(contentPS).startsWith("# Simpler IO Test");
    }

    private ProgrammingExercise getAndTestExerciseDetails(SharingInfoDTO sharingInfo) throws Exception, JsonProcessingException, UnsupportedEncodingException {
        // get Exercise Details

        MvcResult resultED = requestUtilService
                .performMvcRequest(post("/api/programming/sharing/import/basket/exercise-details").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sharingInfo)).accept(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        String contentED = resultED.getResponse().getContentAsString();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.findAndRegisterModules();

        ProgrammingExercise exercise = objectMapper.readerFor(ProgrammingExercise.class).readValue(contentED);
        assertThat(exercise.getTitle()).isEqualTo("JUnit IO Tests");
        assertThat(exercise.getProgrammingLanguage()).isEqualTo(ProgrammingLanguage.JAVA);

        return exercise;
    }

    /**
     * utility method to make the course returned by `programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases()` serializable.
     *
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
            if (e instanceof ProgrammingExercise) {
                ProgrammingExercise pe = (ProgrammingExercise) e;
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void setUpWithMissingExercise() throws Exception {

        SharingSetupInfo emptySetupInfo = new SharingSetupInfo(null, null, null);

        // last step: do Exercise Import
        requestUtilService.performMvcRequest(post("/api/programming/sharing/setup-import").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptySetupInfo)).accept(MediaType.APPLICATION_JSON)).andExpect(status().is5xxServerError());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseInfosWrongChecksum() throws Exception {

        SharingInfoDTO sharingInfo = new SharingInfoDTO("Some Basket Token", TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN, "Invalid Checksum", 0);

        requestUtilService.performMvcRequest(post("/api/programming/sharing/import/basket/exercise-details").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sharingInfo)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProblemStatementWrongChecksum() throws Exception {

        SharingInfoDTO sharingInfo = new SharingInfoDTO("SomeBasketToken", TEST_RETURN_URL, SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN, "Invalid Checksum", 0);

        requestUtilService.performMvcRequest(post("/api/programming/sharing/import/basket/problem-statement").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sharingInfo)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    private String importBasket() throws Exception {
        String sampleBasket = IOUtils.toString(Objects.requireNonNull(this.getClass().getResource("./basket/sampleBasket.json")), StandardCharsets.UTF_8);

        URI basketURI = new URI(SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/basket/" + SAMPLE_BASKET_TOKEN);
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(true);
        mockServer = builder.build();

        final ResponseActions responseActions = mockServer.expect(ExpectedCount.once(), requestTo(basketURI)).andExpect(method(HttpMethod.GET));
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
