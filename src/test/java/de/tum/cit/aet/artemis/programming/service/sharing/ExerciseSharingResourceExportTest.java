package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * this class tests all export features of the ExerciseSharingResource class
 */

class ExerciseSharingResourceExportTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "exercisesharingexporttests";

    public static final String TEST_CALLBACK_URL = "http://testing/xyz1";

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private RequestUtilService requestUtilService;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlatform();
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
    }

    private ProgrammingExercise programmingExercise1;

    @BeforeEach
    void setupExercise() throws Exception {

        programmingExercise1 = ExerciseUtilService.getFirstExerciseWithType(programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases(),
                ProgrammingExercise.class);

        programmingExerciseUtilService.createGitRepository();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportWithMissingRepositories() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, 0);
        Optional<ProgrammingExercise> progrO = courses.getFirst().getExercises().stream().filter(e -> e instanceof ProgrammingExercise).map(e -> (ProgrammingExercise) e)
                .findFirst();
        assertThat(progrO).isPresent();
        ProgrammingExercise progr = progrO.get();
        MvcResult result = requestUtilService
                .performMvcRequest(post("/api/programming/sharing/export/" + progr.getId()).content(TEST_CALLBACK_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN + ";charset=UTF-8")).andExpect(status().isInternalServerError()).andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).startsWith("An error occurred while exporting the exercise");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportWithRepositoriesAsInstructor() throws Exception {
        testExportWithRepositories();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testExportWithRepositoriesAsEditor() throws Exception {
        testExportWithRepositories();
    }

    private void testExportWithRepositories() throws Exception {

        MvcResult result = requestUtilService
                .performMvcRequest(post("/api/programming/sharing/export/" + programmingExercise1.getId()).content(TEST_CALLBACK_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN + ";charset=UTF-8")).andExpect(status().isOk()).andReturn();
        String jsonResult = result.getResponse().getContentAsString();

        String redirectLink = objectMapper.readerFor(String.class).readValue(jsonResult);

        URI redirectUrl = new URI(redirectLink);
        assertThat(redirectLink).startsWith(SharingPlatformMockProvider.SHARING_BASEURL);

        List<NameValuePair> params = URLEncodedUtils.parse(redirectUrl, StandardCharsets.UTF_8);

        assertThat(params).hasSize(2);
        assertThat(params.get(0).getName()).isEqualTo("exerciseUrl");
        assertThat(params.get(1).getName()).isEqualTo("callBack");

        String exerciseUrl = params.get(0).getValue();
        String callBackUrl = params.get(1).getValue();
        assertThat(callBackUrl).isEqualTo(TEST_CALLBACK_URL);

        MockHttpServletRequestBuilder requestBuilder = convertToGetRequestBuilder(exerciseUrl);

        MvcResult zipResult = requestUtilService.performMvcRequest(requestBuilder).andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM)).andExpect(status().isOk())
                .andReturn();

        byte[] binaryData = zipResult.getResponse().getContentAsByteArray();

        assertThat(binaryData).hasSizeGreaterThan(100); // a very trivial test that a valid zip is transferred
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void testExportWithRepositoriesAsStudentNotAuthorized() throws Exception {
        requestUtilService
                .performMvcRequest(post("/api/programming/sharing/export/" + programmingExercise1.getId()).content(TEST_CALLBACK_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(status().is4xxClientError());
    }

    private static MockHttpServletRequestBuilder convertToGetRequestBuilder(String exerciseUrl) throws URISyntaxException {
        URI exerciseUri = new URI(exerciseUrl);
        List<NameValuePair> paramsExerciseUri = URLEncodedUtils.parse(exerciseUri, StandardCharsets.UTF_8);

        // reconstruct request with correctly encoded parameters from exerciseUri
        MockHttpServletRequestBuilder requestBuilder = get(exerciseUri.getPath());
        for (NameValuePair param : paramsExerciseUri) {
            requestBuilder = requestBuilder.queryParam(param.getName(), param.getValue());
        }
        return requestBuilder;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExerciseZipdownloadInvalidToken() throws Exception {

        requestUtilService.performMvcRequest(get("/api/programming/sharing/export/INVALID_EXERCISE_TOKEN").queryParam("sec", "someSec")).andExpect(status().isUnauthorized());
    }

}
