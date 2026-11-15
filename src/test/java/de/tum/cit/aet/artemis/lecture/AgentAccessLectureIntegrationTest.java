package de.tum.cit.aet.artemis.lecture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Creating lectures and adding units with a personal access token: units are always drafts.
 */
class AgentAccessLectureIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "agentlecture";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Course course;

    private Lecture lecture;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        lecture = lectureUtilService.createLecture(course, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        personalAccessTokenRepository.deleteAll();
    }

    @Test
    void anEditorTokenCreatesALecture() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.LECTURES_WRITE);
        var body = JsonObjectMapper.get().writeValueAsString(Map.of("title", "Lecture from an agent", "course", Map.of("id", course.getId())));

        perform(post("/api/lecture/lectures").contentType(MediaType.APPLICATION_JSON).content(body), token).andExpect(status().isCreated());
    }

    @Test
    void aTutorTokenCannotCreateALecture() throws Exception {
        SavedToken token = tokenOf("tutor1", AgentScope.LECTURES_WRITE);
        var body = JsonObjectMapper.get().writeValueAsString(Map.of("title", "Not allowed", "course", Map.of("id", course.getId())));

        perform(post("/api/lecture/lectures").contentType(MediaType.APPLICATION_JSON).content(body), token).andExpect(status().isForbidden());
    }

    @Test
    void aUnitWithoutAFutureReleaseDateIsRejected() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.LECTURES_WRITE);

        perform(unitRequest(Map.of("name", "Slides", "videoSource", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")), token).andExpect(status().isBadRequest());
        perform(unitRequest(Map.of("name", "Slides", "releaseDate", ZonedDateTime.now().minusDays(1).toString(), "videoSource", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")),
                token).andExpect(status().isBadRequest());
    }

    @Test
    void aUnitWithAFutureReleaseDateIsCreated() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.LECTURES_WRITE);

        perform(unitRequest(Map.of("name", "Video", "releaseDate", ZonedDateTime.now().plusDays(7).toString(), "videoSource", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")),
                token).andExpect(status().isCreated());
    }

    @Test
    void theProcessingStatusOfTheUnitsCanBeRead() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.LECTURES_WRITE);

        perform(get("/api/lecture/lectures/" + lecture.getId() + "/lecture-units/statuses"), token).andExpect(status().isOk());
    }

    @Test
    void aTokenWithoutTheLectureScopeIsForbidden() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.COMPETENCIES_WRITE);

        perform(get("/api/lecture/lectures/" + lecture.getId() + "/lecture-units/statuses"), token).andExpect(status().isForbidden());
    }

    @Test
    void slidesAreUploadedAsADraftWhenUnitAndAttachmentAreReleasedInTheFuture() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.LECTURES_WRITE);
        String release = ZonedDateTime.now().plusDays(7).toString();

        perform(slidesRequest(Map.of("name", "Slides", "releaseDate", release), Map.of("name", "Lecture slides", "attachmentType", "FILE", "releaseDate", release)), token)
                .andExpect(status().isCreated());
    }

    @Test
    void slidesWhoseAttachmentIsAlreadyVisibleAreRejected() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.LECTURES_WRITE);
        String release = ZonedDateTime.now().plusDays(7).toString();

        perform(slidesRequest(Map.of("name", "Slides", "releaseDate", release), Map.of("name", "Lecture slides", "attachmentType", "FILE")), token)
                .andExpect(status().isBadRequest());
    }

    private AbstractMockHttpServletRequestBuilder<?> slidesRequest(Map<String, Object> unit, Map<String, Object> attachment) throws Exception {
        var unitPart = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE,
                JsonObjectMapper.get().writeValueAsString(unit).getBytes(StandardCharsets.UTF_8));
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE,
                JsonObjectMapper.get().writeValueAsString(attachment).getBytes(StandardCharsets.UTF_8));
        var filePart = new MockMultipartFile("file", "slides.pdf", MediaType.APPLICATION_PDF_VALUE, twoPagePdf());
        return MockMvcRequestBuilders.multipart("/api/lecture/lectures/" + lecture.getId() + "/attachment-video-units").file(unitPart).file(attachmentPart).file(filePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private static byte[] twoPagePdf() throws IOException {
        try (var output = new ByteArrayOutputStream(); var document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private AbstractMockHttpServletRequestBuilder<?> unitRequest(Map<String, Object> unit) throws Exception {
        var part = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE,
                JsonObjectMapper.get().writeValueAsString(unit).getBytes(StandardCharsets.UTF_8));
        return MockMvcRequestBuilders.multipart("/api/lecture/lectures/" + lecture.getId() + "/attachment-video-units").file(part).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private SavedToken tokenOf(String loginWithoutPrefix, AgentScope... scopes) {
        long userId = userUtilService.getUserByLogin(TEST_PREFIX + loginWithoutPrefix).getId();
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(scopes), Duration.ofDays(1));
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> requestBuilder, SavedToken token) throws Exception {
        return request.performMvcRequest(requestBuilder.header(HttpHeaders.AUTHORIZATION, token.bearer()));
    }
}
