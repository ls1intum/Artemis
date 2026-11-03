package de.tum.cit.aet.artemis.core;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.dto.ExamUserDTO;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FileIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "fileintegration";

    /**
     * The filename every stored attachment in this class uses. The tests address the files through the endpoints, which read the filename off the attachment, so one name is
     * enough and it keeps the expected paths readable.
     */
    private static final String STORED_ATTACHMENT_FILENAME = "dummy.pdf";

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepo;

    @Autowired
    private LectureUnitCompletionRepository lectureUnitCompletionRepository;

    @Autowired
    private LectureTestRepository lectureRepo;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IrisRequestMockProvider irisRequestMockProvider;

    @BeforeEach
    void initTestCase() {
        irisRequestMockProvider.enableMockingOfRequests();
        irisRequestMockProvider.mockLectureUnitVisibilityWebhookRunResponse(dto -> {
        }, ExpectedCount.manyTimes());

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    @AfterEach
    void tearDown() throws Exception {
        irisRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testUploadExamUserSignature() throws Exception {
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course, 1);
        var user = new ExamUserDTO(TEST_PREFIX + "student1", null, null, null, null, null, "", "", true, true, true, true, null, null, null, null, null, null, null, null);
        var file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());

        ExamUser updateExamUserResponse = request.postWithMultipartFile("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/exam-users", user, "examUserDTO", file,
                ExamUser.class, HttpStatus.OK);
        String requestUrl = "%s%s".formatted(ARTEMIS_FILE_PATH_PREFIX, updateExamUserResponse.getSigningImagePath());
        byte[] getUserSignatureResponse = request.get(requestUrl, HttpStatus.OK, byte[].class);

        assertThat(getUserSignatureResponse).isEqualTo(file.getBytes());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetTemplateFile() throws Exception {
        String javaReadme = request.get("/api/core/files/templates/JAVA/PLAIN_MAVEN", HttpStatus.OK, String.class);
        assertThat(javaReadme).isNotEmpty();
        String cReadme = request.get("/api/core/files/templates/C/GCC", HttpStatus.OK, String.class);
        assertThat(cReadme).isNotEmpty();
        String pythonReadme = request.get("/api/core/files/templates/PYTHON", HttpStatus.OK, String.class);
        assertThat(pythonReadme).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void templateReturnsProjectReadmeAndClosesStream() throws Exception {
        var resource = Mockito.mock(Resource.class);
        var stream = Mockito.spy(new ByteArrayInputStream("project readme".getBytes(StandardCharsets.UTF_8)));
        Mockito.doReturn(resource).when(resourceLoaderService).getResource(Path.of("templates", "java", "plain_maven", "readme"));
        Mockito.when(resource.exists()).thenReturn(true);
        Mockito.when(resource.getInputStream()).thenReturn(stream);

        mockMvc.perform(get("/api/core/files/templates/JAVA/PLAIN_MAVEN")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().bytes("project readme".getBytes(StandardCharsets.UTF_8)));
        Mockito.verify(stream).close();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void templateFallsBackToLanguageReadmeWhenProjectReadmeIsMissing() throws Exception {
        var missing = Mockito.mock(Resource.class);
        var fallback = Mockito.mock(Resource.class);
        Mockito.doReturn(missing).when(resourceLoaderService).getResource(Path.of("templates", "java", "plain_maven", "readme"));
        Mockito.doReturn(fallback).when(resourceLoaderService).getResource(Path.of("templates", "java", "readme"));
        Mockito.when(fallback.getInputStream()).thenReturn(new ByteArrayInputStream("fallback".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/core/files/templates/JAVA/PLAIN_MAVEN")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().bytes("fallback".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void templateLoadsLanguageReadmeWithoutProjectType() throws Exception {
        var resource = Mockito.mock(Resource.class);
        Mockito.doReturn(resource).when(resourceLoaderService).getResource(Path.of("templates", "python", "readme"));
        Mockito.when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("python".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/core/files/templates/PYTHON")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().bytes("python".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void templateReturnsNotFoundWhenReadmeCannotBeRead() throws Exception {
        var resource = Mockito.mock(Resource.class);
        Mockito.doReturn(resource).when(resourceLoaderService).getResource(Path.of("templates", "python", "readme"));
        Mockito.when(resource.getInputStream()).thenThrow(new IOException("missing readme"));

        mockMvc.perform(get("/api/core/files/templates/PYTHON")).andExpect(status().isNotFound()).andExpect(content().bytes(new byte[0]));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void templateReturnsUtf8CodeOfConductAndClosesStream() throws Exception {
        var resource = Mockito.mock(Resource.class);
        var stream = Mockito.spy(new ByteArrayInputStream("Respect für alle".getBytes(StandardCharsets.UTF_8)));
        Mockito.doReturn(resource).when(resourceLoaderService).getResource(Path.of("templates", "codeofconduct", "README.md"));
        Mockito.when(resource.getInputStream()).thenReturn(stream);

        mockMvc.perform(get("/api/core/files/templates/code-of-conduct")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Respect für alle"));
        Mockito.verify(stream).close();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void templatePreservesCodeOfConductIoFailure() throws Exception {
        var resource = Mockito.mock(Resource.class);
        Mockito.doReturn(resource).when(resourceLoaderService).getResource(Path.of("templates", "codeofconduct", "README.md"));
        Mockito.when(resource.getInputStream()).thenThrow(new IOException("missing conduct"));

        var result = mockMvc.perform(get("/api/core/files/templates/code-of-conduct")).andExpect(status().isServiceUnavailable()).andExpect(content().string("missing conduct"))
                .andReturn();
        assertThat(result.getResolvedException()).isInstanceOf(IOException.class).hasMessage("missing conduct");
    }

    @ParameterizedTest
    @ValueSource(strings = { "JAVA/PLAIN_MAVEN", "PYTHON", "code-of-conduct" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void staticTemplatesAreAvailableToSignedInUsers(String template) throws Exception {
        mockMvc.perform(get("/api/core/files/templates/" + template)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @ParameterizedTest
    @ValueSource(strings = { "JAVA/PLAIN_MAVEN", "PYTHON", "code-of-conduct" })
    @WithAnonymousUser
    void staticTemplatesRejectAnonymousUsers(String template) throws Exception {
        mockMvc.perform(get("/api/core/files/templates/" + template)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetCodeOfConductTemplate() throws Exception {
        var template = request.get("/api/core/files/templates/code-of-conduct", HttpStatus.OK, String.class);
        assertThat(template).startsWith("<!-- Code of Conduct Template");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetUnreleasedAttachmentVideoUnitAsTutor() throws Exception {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);
        lecture.setTitle("Test title");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));

        // create unreleased attachment video unit
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        attachmentVideoUnit.setLecture(lecture);
        attachmentVideoUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));

        lectureRepo.save(lecture);
        attachmentVideoUnit = attachmentVideoUnitRepo.save(attachmentVideoUnit);

        String requestUrl = "%s%s".formatted(ARTEMIS_FILE_PATH_PREFIX, attachmentVideoUnit.getAttachment().getLink());
        request.get(requestUrl, HttpStatus.OK, String.class);
    }

    /**
     * The lecture attachment route is mapped under two spellings of its path and resolves the file from the attachment rather than from the request, so a file that lies under
     * the lecture attachment directory has to come back under either one. The route reaches the attachment through the units of the lecture, since an attachment names no
     * lecture of its own any more. Post markdown and client caches keep asking for whichever spelling they recorded, which is what keeps
     * both mappings load-bearing.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetMigratedLectureAttachmentUnderEitherPathSpelling() throws Exception {
        byte[] content = "lecture attachment content".getBytes();
        Attachment attachment = createLectureAttachmentWithStoredFile(content);
        long lectureId = attachment.getAttachmentVideoUnit().getLecture().getId();

        String requestedName = attachment.getName() + ".pdf";
        assertThat(request.get("/api/core/files/attachments/lecture/" + lectureId + "/" + requestedName, HttpStatus.OK, byte[].class)).isEqualTo(content);
        assertThat(request.get("/api/core/files/attachments/lectures/" + lectureId + "/" + requestedName, HttpStatus.OK, byte[].class)).isEqualTo(content);
    }

    /**
     * The same file, asked for through the attachment video unit that owns it. The attachment is served under its unit like every other one, and every unit route answers for it
     * even though the file itself never left the lecture attachment directory. That is the whole point of separating where a file is served from where it is stored.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetMigratedLectureAttachmentThroughItsAttachmentVideoUnit() throws Exception {
        byte[] content = "lecture attachment content".getBytes();
        Attachment attachment = createLectureAttachmentWithStoredFile(content);
        long attachmentVideoUnitId = attachment.getAttachmentVideoUnit().getId();

        assertThat(attachment.getLink()).isEqualTo("attachments/attachment-video-units/" + attachmentVideoUnitId + "/" + STORED_ATTACHMENT_FILENAME);
        assertThat(request.get("/api/core/files/" + attachment.getLink(), HttpStatus.OK, byte[].class)).isEqualTo(content);
        assertThat(
                request.get("/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnitId + "/student/" + STORED_ATTACHMENT_FILENAME, HttpStatus.OK, byte[].class))
                .isEqualTo(content);
    }

    /**
     * The same for a course icon, where the two spellings differ in the position of the id segment rather than in one word.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseIconUnderEitherPathSpelling() throws Exception {
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        String filename = "CourseIcon_" + TEST_PREFIX + ".png";
        byte[] iconContent = "icon".getBytes();
        FileUtils.writeByteArrayToFile(FilePathConverter.getCourseIconFilePath().resolve(filename).toFile(), iconContent);

        course.setCourseIcon("course/icons/" + course.getId() + "/" + filename);
        courseRepository.save(course);

        assertThat(request.get("/api/core/files/course/icons/" + course.getId() + "/" + filename, HttpStatus.OK, byte[].class)).isEqualTo(iconContent);
        assertThat(request.get("/api/core/files/courses/" + course.getId() + "/icons/" + filename, HttpStatus.OK, byte[].class)).isEqualTo(iconContent);
    }

    /**
     * A client can hold the URL of a profile picture the user has removed since. That is a missing file, not a server error.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProfilePictureOfUserWithoutPictureIsNotFound() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setImageUrl(null);
        userTestRepository.save(user);

        request.get("/api/core/files/users/" + user.getId() + "/profile-pictures/ProfilePicture_removed.jpeg", HttpStatus.NOT_FOUND, byte[].class);
    }

    private static final String SCRIPTED_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(document.domain)</script></svg>";

    private static final byte[] PNG_IMAGE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 };

    private int uploadProfilePicture(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PUT, "/api/account/profile-picture").file(file)).andReturn().getResponse().getStatus();
    }

    /**
     * The declared content type and the filename both come from the client, so neither may turn a document with scripts into a stored profile picture.
     */
    @ParameterizedTest
    @ValueSource(strings = { "picture.png", "picture.svg", "picture.html" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUploadProfilePictureRejectsScriptedDocumentDeclaredAsImage(String filename) throws Exception {
        var file = new MockMultipartFile("file", filename, "image/png", SCRIPTED_SVG.getBytes(StandardCharsets.UTF_8));

        assertThat(uploadProfilePicture(file)).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getImageUrl()).isNull();
    }

    /**
     * The stored extension decides the content type the picture is served with, so it follows from the content and not from the uploaded filename.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUploadProfilePictureTakesExtensionFromContent() throws Exception {
        var file = new MockMultipartFile("file", "picture.svg", "image/png", PNG_IMAGE);

        assertThat(uploadProfilePicture(file)).isEqualTo(HttpStatus.OK.value());
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getImageUrl()).endsWith(".png");
    }

    /**
     * A file stored with an active content type, such as an SVG profile picture uploaded before uploads were checked, must render without scripts when opened directly. That
     * holds for the REST endpoint and for the static handler that still serves the upload directory.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStoredScriptedSvgIsServedInSandbox() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        String filename = "ProfilePicture_" + TEST_PREFIX + ".svg";
        FileUtils.writeByteArrayToFile(FilePathConverter.getProfilePictureFilePath().resolve(filename).toFile(), SCRIPTED_SVG.getBytes(StandardCharsets.UTF_8));
        user.setImageUrl(filename);
        userTestRepository.save(user);

        for (String url : List.of("/api/core/files/users/" + user.getId() + "/profile-pictures/" + filename, "/user/profile-pictures/" + filename)) {
            String policy = mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/svg+xml")).andReturn().getResponse()
                    .getHeader("Content-Security-Policy");
            assertThat(policy).as("policy of %s", url).contains("sandbox", "default-src 'none'").doesNotContain("unsafe-inline' 'unsafe-eval'");
        }
    }

    /**
     * Browsers refuse to show a PDF in a sandboxed document, so a PDF keeps the application-wide policy.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testPdfIsNotServedInSandbox() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        userUtilService.changeUser(TEST_PREFIX + "student1");
        String policy = mockMvc.perform(get("/api/core/files/attachments/lectures/" + lecture.getId() + "/merge-pdf")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(policy).doesNotContain("sandbox");
    }

    /**
     * A post written before this release embeds everything after {@code attachments/} of the attachment link the server was serving at the time, and the client re-expands that
     * fragment against {@code api/core/files/attachments/}. Those posts are user-authored prose in the database that no migration reaches, so the singular spelling they carry
     * has to keep resolving for as long as the posts exist. This walks the two fragments the editor records for an attachment video unit reference, the attachment itself and
     * its student version.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetAttachmentVideoUnitUnderThePathSpellingRecordedInPostMarkdown() throws Exception {
        byte[] dummyContent = "dummy pdf content".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);

        String attachmentFragment = "attachment-unit/" + attachmentVideoUnit.getId() + "/dummy.pdf";
        String studentVersionFragment = "attachment-unit/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        assertThat(request.get("/api/core/files/attachments/" + attachmentFragment, HttpStatus.OK, byte[].class)).isEqualTo(dummyContent);
        assertThat(request.get("/api/core/files/attachments/" + studentVersionFragment, HttpStatus.OK, byte[].class)).isEqualTo(dummyContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void uploadImageMarkdownAsStudent_forbidden() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        // upload file
        request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=true&courseId=" + course.getId(), file.getOriginalFilename(), "file", file, JsonNode.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void uploadImageMarkdownAsTutorInForeignCourse_forbidden() throws Exception {
        var course = courseUtilService.addEmptyCourse();
        var file = new MockMultipartFile("file", "image.png", "image/png", "image".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/core/markdown-file-upload").file(file).param("courseId", course.getId().toString())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void uploadImageMarkdownAsTutor_nonExistingCourse_forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=true&courseId=" + Long.MAX_VALUE, file.getOriginalFilename(), "file", file, JsonNode.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void uploadImageMarkdownAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=true&courseId=" + course.getId(), file.getOriginalFilename(), "file", file,
                JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asString();
        assertThat(responsePath).contains("markdown");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void uploadImageMarkdownOutsideOfACourseAsAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/core/admin/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class,
                HttpStatus.CREATED);
        assertThat(response.get("path").asString()).contains("markdown");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void uploadImageMarkdownOutsideOfACourseAsInstructor_forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        request.postWithMultipartFile("/api/core/admin/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void markdownUploadReturnsSanitizedJson(boolean keepFileName) throws Exception {
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        assertSanitizedUpload("/api/core/markdown-file-upload?courseId=" + course.getId(), keepFileName);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminMarkdownUploadReturnsSanitizedJson(boolean keepFileName) throws Exception {
        assertSanitizedUpload("/api/core/admin/markdown-file-upload", keepFileName);
    }

    private void assertSanitizedUpload(String url, boolean keepFileName) throws Exception {
        var file = new MockMultipartFile("file", "../<script>\"unsafe.png", "image/png", "image".getBytes(StandardCharsets.UTF_8));
        var result = mockMvc.perform(multipart(url).file(file).param("keepFileName", Boolean.toString(keepFileName)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()).andExpect(content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(1);
        var path = body.get("path").asString();
        assertThat(path).startsWith("/api/core/files/markdown/Markdown_").endsWith(".png").doesNotContain("<", ">", "\"", "..", "unsafe.png/");
        assertThat(path.substring("/api/core/files/markdown/".length())).matches("[A-Za-z0-9_.-]+\\.png");
        if (keepFileName) {
            assertThat(path).endsWith(".__script__unsafe.png");
        }
        else {
            assertThat(path).doesNotContain("unsafe");
        }
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(path);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void markdownUploadRejectsHtml() throws Exception {
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        assertUploadRejectsHtml("/api/core/markdown-file-upload?courseId=" + course.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminMarkdownUploadRejectsHtml() throws Exception {
        assertUploadRejectsHtml("/api/core/admin/markdown-file-upload");
    }

    private void assertUploadRejectsHtml(String url) throws Exception {
        var file = new MockMultipartFile("file", "image.png", "image/png", "image".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart(url).file(file).accept(MediaType.TEXT_HTML)).andExpect(status().isNotAcceptable());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void uploadFileMarkdownUnsupportedFileExtensionAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.txt", "application/json", "some data".getBytes());
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        // upload file
        request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=true&courseId=" + course.getId(), file.getOriginalFilename(), "file", file, JsonNode.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetLecturePdfAttachmentsMerged_InvalidLectureId() throws Exception {
        request.get("/api/core/files/attachments/lectures/" + 999999999 + "/merge-pdf", HttpStatus.NOT_FOUND, byte[].class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetLecturePdfAttachmentsMerged() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        var units = lecture.getLectureUnits();
        userUtilService.changeUser(TEST_PREFIX + "student1");
        ZonedDateTime now = ZonedDateTime.now();
        callAndCheckMergeResult(lecture, 5);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        List<LectureUnit> expectedCompletedUnits = List.of(units.getFirst(), units.get(2));
        for (var unit : expectedCompletedUnits) {
            var completion = lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(unit.getId(), student.getId());
            assertThat(completion).isPresent();
            assertThat(completion.get().getCompletedAt()).isCloseTo(now, within(2, ChronoUnit.SECONDS));
        }

        // Unit 2 (index 1) is an image and not included in the merged pdf
        var nonCompletedUnit = lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(units.get(1).getId(), student.getId());
        assertThat(nonCompletedUnit).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetLecturePdfAttachmentsMerged_HiddenSlidesWithoutStudentVersionReturnsNotFound() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        AttachmentVideoUnit unit = lecture.getLectureUnits().stream().filter(AttachmentVideoUnit.class::isInstance).map(AttachmentVideoUnit.class::cast)
                .filter(lectureUnit -> lectureUnit.getAttachment().getLink().endsWith(".pdf")).findFirst().orElseThrow();
        // The upload schedules slide splitting asynchronously. Create the relevant slide explicitly so this test does
        // not depend on the background processor winning a race with the assertion.
        Slide slide = new Slide();
        slide.setAttachmentVideoUnit(unit);
        slide.setSlideNumber(1);
        slide.setSlideImagePath("attachments/attachment-unit/" + unit.getId() + "/slide/1.png");
        slide.setHidden(ZonedDateTime.now().plusDays(1));
        slideRepository.save(slide);
        clearStoredStudentVersion(unit.getAttachment());

        userUtilService.changeUser(TEST_PREFIX + "student1");

        request.get("/api/core/files/attachments/lectures/" + lecture.getId() + "/merge-pdf", HttpStatus.NOT_FOUND, byte[].class);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        lecture.getLectureUnits().stream().filter(AttachmentVideoUnit.class::isInstance)
                .forEach(lectureUnit -> assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), student.getId())).isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetLecturePdfAttachmentsMerged_DoesNotCompleteExternalPdf() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        var externalUnit = (AttachmentVideoUnit) lecture.getLectureUnits().get(2);
        externalUnit.getAttachment().setLink("https://example.com/external.pdf");
        attachmentRepo.save(externalUnit.getAttachment());
        userUtilService.changeUser(TEST_PREFIX + "student1");

        callAndCheckMergeResult(lecture, 3);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lecture.getLectureUnits().getFirst().getId(), student.getId())).isPresent();
        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(externalUnit.getId(), student.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetLecturePdfAttachmentsMerged_MissingFileDoesNotCompleteUnits(boolean allFilesMissing) throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        var pdfUnits = lecture.getLectureUnits().stream().map(AttachmentVideoUnit.class::cast).filter(unit -> unit.getAttachment().getLink().endsWith(".pdf")).toList();
        for (var unit : allFilesMissing ? pdfUnits : List.of(pdfUnits.getFirst())) {
            Files.delete(unit.getAttachment().fileLocation().orElseThrow().path());
        }
        userUtilService.changeUser(TEST_PREFIX + "student1");

        request.get("/api/core/files/attachments/lectures/" + lecture.getId() + "/merge-pdf", HttpStatus.NOT_FOUND, byte[].class);

        assertLectureUnitsNotCompleted(lecture);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetLecturePdfAttachmentsMerged_CorruptFileDoesNotCompleteUnits() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        var firstUnit = (AttachmentVideoUnit) lecture.getLectureUnits().getFirst();
        FileUtils.writeStringToFile(firstUnit.getAttachment().fileLocation().orElseThrow().path().toFile(), "corrupt PDF", StandardCharsets.UTF_8);
        userUtilService.changeUser(TEST_PREFIX + "student1");

        request.get("/api/core/files/attachments/lectures/" + lecture.getId() + "/merge-pdf", HttpStatus.INTERNAL_SERVER_ERROR, byte[].class);

        assertLectureUnitsNotCompleted(lecture);
    }

    private void assertLectureUnitsNotCompleted(Lecture lecture) {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(lecture.getLectureUnits()).allSatisfy(unit -> assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(unit.getId(), student.getId())).isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetLecturePdfAttachmentsMerged_TutorAccessToUnreleasedUnits() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();

        adjustReleaseDateToFuture(lecture);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        // The unit is hidden but a tutor can still see it
        // -> the merged result should contain the unit
        callAndCheckMergeResult(lecture, 5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetLecturePdfAttachmentsMerged_NoAccessToUnreleasedUnits() throws Exception {
        // The test setup needs elevated privileges, we later switch to a student for the test execution
        Lecture lecture = createLectureWithLectureUnits();

        adjustReleaseDateToFuture(lecture);
        userUtilService.changeUser(TEST_PREFIX + "student1");

        // The unit is hidden, students should not see it in the merged result
        callAndCheckMergeResult(lecture, 2);
    }

    private void adjustReleaseDateToFuture(Lecture lecture) {
        var unit = (AttachmentVideoUnit) lecture.getLectureUnits().stream().min(Comparator.comparing(LectureUnit::getId)).orElseThrow();
        var targetTime = ZonedDateTime.now().plusHours(2);
        unit.getAttachment().setReleaseDate(targetTime);
        unit.setReleaseDate(targetTime);
        attachmentRepo.save(unit.getAttachment());
        attachmentVideoUnitRepo.save(unit);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetLecturePdfAttachmentsMerged_correctOrder() throws Exception {
        // The test setup needs elevated privileges, we later switch to a student for the test execution
        Lecture lecture = createLectureWithLectureUnits();

        // Change order of units
        List<Long> lectureUnitIds = lecture.getLectureUnits().stream().map(LectureUnit::getId).collect(Collectors.toCollection(ArrayList::new));
        // move unit at index 2 to the beginning
        Long unitId = lectureUnitIds.remove(2);
        lectureUnitIds.addFirst(unitId);
        lecture.reorderLectureUnits(lectureUnitIds);
        lectureRepo.save(lecture);

        userUtilService.changeUser(TEST_PREFIX + "student1");

        try (PDDocument mergedDoc = retrieveMergeResult(lecture)) {
            assertThat(mergedDoc.getNumberOfPages()).isEqualTo(5);
            PDPage firstPage = mergedDoc.getPage(0);
            // Verify that attachment 3 (created with a special crop box in createLectureWithLectureUnits) was moved to the start
            // and is now the first page of the merged pdf
            assertThat(firstPage.getCropBox().getHeight()).isEqualTo(4);
        }
    }

    private void callAndCheckMergeResult(Lecture lecture, int expectedPages) throws Exception {
        try (PDDocument mergedDoc = retrieveMergeResult(lecture)) {
            assertThat(mergedDoc.getNumberOfPages()).isEqualTo(expectedPages);
        }
    }

    private PDDocument retrieveMergeResult(Lecture lecture) throws Exception {
        byte[] receivedFile = request.get("/api/core/files/attachments/lectures/" + lecture.getId() + "/merge-pdf", HttpStatus.OK, byte[].class);

        assertThat(receivedFile).isNotEmpty();
        return Loader.loadPDF(receivedFile);
    }

    private Lecture createLectureWithLectureUnits() throws Exception {
        return createLectureWithLectureUnits(HttpStatus.CREATED);
    }

    private Lecture createLectureWithLectureUnits(HttpStatus expectedStatus) throws Exception {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);

        lecture.setTitle("Test title");
        lecture.setDescription("Test");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));
        lectureRepo.save(lecture);

        // create pdf file 1
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc1 = new PDDocument()) {
            doc1.addPage(new PDPage());
            doc1.addPage(new PDPage());
            doc1.addPage(new PDPage());
            doc1.save(outputStream);
            MockMultipartFile file1 = new MockMultipartFile("file", "file.pdf", "application/json", outputStream.toByteArray());
            lecture.addLectureUnit(uploadAttachmentVideoUnit(lecture, file1, expectedStatus));
        }

        // create image file
        MockMultipartFile file2 = new MockMultipartFile("file", "filename2.png", "application/json", "some text".getBytes());
        lecture.addLectureUnit(uploadAttachmentVideoUnit(lecture, file2, expectedStatus));

        // create pdf file 3
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc2 = new PDDocument()) {
            // Add first page with extra cropBox to make it distinguishable in the later tests
            PDPage page = new PDPage();
            page.setCropBox(new PDRectangle(1, 2, 3, 4));
            doc2.addPage(page);
            doc2.addPage(new PDPage());
            doc2.save(outputStream);
            MockMultipartFile file3 = new MockMultipartFile("file", "filename3.pdf", "application/json", outputStream.toByteArray());
            lecture.addLectureUnit(uploadAttachmentVideoUnit(lecture, file3, expectedStatus));
        }

        // Collect units freshly from the database to prevent issues when persisting the lecture again
        lecture.setLectureUnits(attachmentVideoUnitRepo.findAllByLectureIdAndAttachmentType(lecture.getId(), AttachmentType.FILE).stream().map(unit -> (LectureUnit) unit)
                .collect(Collectors.toCollection(ArrayList::new)));

        if (expectedStatus == HttpStatus.CREATED) {
            // Each PDF is split into slides asynchronously, and the student downloads stay closed until its slides are complete: 3 + 2 pages
            List<Long> unitIds = lecture.getLectureUnits().stream().map(LectureUnit::getId).toList();
            await().untilAsserted(() -> {
                assertThat(unitIds.stream().mapToInt(id -> slideRepository.findAllByAttachmentVideoUnitId(id).size()).sum()).isEqualTo(5);
                assertThat(slideRepository.findAttachmentVideoUnitIdsWithSlidesOfOtherRevision(unitIds)).isEmpty();
            });
        }

        return lecture;
    }

    private AttachmentVideoUnit uploadAttachmentVideoUnit(Lecture lecture, MockMultipartFile file, HttpStatus expectedStatus) throws Exception {
        AttachmentVideoUnit attachmentVideoUnit = LectureFactory.generateAttachmentVideoUnit();
        Attachment attachment = attachmentVideoUnit.getAttachment();
        attachmentVideoUnit.setAttachment(null);
        attachment.setAttachmentVideoUnit(null);
        MockMultipartFile attachmentFile = new MockMultipartFile("attachment", "", "application/json", objectMapper.writeValueAsBytes(attachment));

        return request.postWithMultipartFiles("/api/lecture/lectures/" + lecture.getId() + "/attachment-video-units", attachmentVideoUnit, "attachmentVideoUnit",
                List.of(attachmentFile, file), AttachmentVideoUnit.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void testGetAttachmentVideoUnitFileAsEditor() throws Exception {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);

        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        attachmentVideoUnit.setLecture(lecture);
        Attachment attachment = attachmentVideoUnit.getAttachment();

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);
        attachmentVideoUnitRepo.save(attachmentVideoUnit);

        Long courseId = lecture.getCourse().getId();
        Long attachmentVideoUnitId = attachmentVideoUnit.getId();

        request.get("/api/core/files/courses/" + courseId + "/attachment-video-units/" + attachmentVideoUnitId, HttpStatus.OK, byte[].class);
    }

    /**
     * The path names no file here, so a revalidation has to tell by its content type that it keeps the policy of a PDF: browsers copy the policy of a 304 onto the cached
     * file, and a sandboxed PDF is not displayed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void testGetAttachmentVideoUnitPdfNotModifiedIsNotSandboxed() throws Exception {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 0, true);
        String url = "/api/core/files/courses/" + lecture.getCourse().getId() + "/attachment-video-units/" + attachmentVideoUnit.getId();

        String lastModified = mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse()
                .getHeader(HttpHeaders.LAST_MODIFIED);
        String policy = mockMvc.perform(get(url).header(HttpHeaders.IF_MODIFIED_SINCE, lastModified)).andExpect(status().isNotModified()).andReturn().getResponse()
                .getHeader("Content-Security-Policy");
        assertThat(policy).doesNotContain("sandbox");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersion() throws Exception {
        testGetAttachmentVideoUnitAsStudent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetAttachmentVideoUnitAttachmentFilenameSanitization() throws Exception {
        testGetAttachmentVideoUnitAsTutor();
    }

    private void testGetAttachmentVideoUnitAsStudent() throws Exception {
        testGetAttachmentVideoUnit(false);
    }

    private void testGetAttachmentVideoUnitAsTutor() throws Exception {
        testGetAttachmentVideoUnit(true);
    }

    private void testGetAttachmentVideoUnit(boolean isTutor) throws Exception {
        byte[] dummyContent = "dummy pdf content".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);

        String unsanitizedName = "test–file"; // contains en-dash
        Attachment attachment = attachmentVideoUnit.getAttachment();
        attachment.setName(unsanitizedName);
        attachmentRepo.save(attachment);

        String unsanitizedFilename = "AttachmentUnit_2025-05-10T12-10-34_" + unsanitizedName + ".pdf";
        String url = isTutor ? "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/" + unsanitizedFilename
                : "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/" + unsanitizedFilename;

        MvcResult result = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();

        byte[] responseContent = result.getResponse().getContentAsByteArray();
        assertThat(responseContent).isEqualTo(dummyContent);

        String contentDisposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(contentDisposition).isNotNull();
        assertThat(contentDisposition).doesNotContain("-");
        assertThat(contentDisposition).contains("filename=\"test_file.pdf\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUploadAndRetrieveFileForConversation() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);
        var posts = conversationUtilService.createPostsWithinCourse(courseUtilService.createEnrolledCourse(TEST_PREFIX), TEST_PREFIX);
        var conversation = posts.getFirst().getConversation();
        var course = conversation.getCourse();

        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[] { 1, 2, 3, 4, 5 });

        var upload = mockMvc
                .perform(multipart("/api/core/files/courses/" + course.getId() + "/conversations/" + conversation.getId()).file(file).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()).andExpect(content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        JsonNode response = objectMapper.readTree(upload.getResponse().getContentAsString());
        String responsePath = response.get("path").asString();
        assertThat(responsePath).startsWith("/api/core/files/courses/" + course.getId() + "/conversations/" + conversation.getId() + "/").endsWith("_image.png");
        assertThat(upload.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(responsePath);

        byte[] retrievedContent = request.get(responsePath, HttpStatus.OK, byte[].class);
        assertThat(retrievedContent).isEqualTo(file.getBytes());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUploadFileForConversationTooLarge() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);
        var posts = conversationUtilService.createPostsWithinCourse(courseUtilService.createEnrolledCourse(TEST_PREFIX), TEST_PREFIX);
        var conversation = posts.getFirst().getConversation();
        var course = conversation.getCourse();

        byte[] largeContent = new byte[(int) Constants.MAX_FILE_SIZE_COMMUNICATION + 1];
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", largeContent);

        request.postWithMultipartFile("/api/core/files/courses/" + course.getId() + "/conversations/" + conversation.getId(), file.getOriginalFilename(), "file", file,
                JsonNode.class, HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetMarkdownFileWithUnsanitizedFilename() throws Exception {
        mockMvc.perform(get("/api/core/files/markdown/{filename}", "unsafe name.png")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetAttachmentVideoUnitAttachmentRangeRequest() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/dummy.pdf";

        MvcResult result = mockMvc.perform(get(url).header("Range", "bytes=2-5")).andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Accept-Ranges", "bytes")).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(new byte[] { 50, 51, 52, 53 });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersionRangeRequest() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        MvcResult result = mockMvc.perform(get(url).header("Range", "bytes=2-5")).andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Accept-Ranges", "bytes")).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(new byte[] { 50, 51, 52, 53 });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersionFailsClosedWhileRegenerationIsPending() throws Exception {
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithoutStudentVersion("unrestricted instructor content".getBytes());
        saveSlide(attachmentVideoUnit, 1, ZonedDateTime.now().plusDays(1), false);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersionIgnoresHiddenSlidesOfReplacedFile() throws Exception {
        byte[] content = "replacement without hidden slides".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithoutStudentVersion(content);
        saveSlide(attachmentVideoUnit, 1, ZonedDateTime.now().plusDays(1), true);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        MvcResult result = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(content);
    }

    /**
     * After a failed editor split the previous slides are restored while the new file stays stored. Which pages of that file are hidden is then unknown, even when the
     * restored slides hide nothing, so the full file must not be served.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersionFailsClosedWhileTheSlidesBelongToAnotherFile() throws Exception {
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithoutStudentVersion("new file with pages hidden by the instructor".getBytes());
        saveSlide(attachmentVideoUnit, 1, null, false, -7, "slides-of-the-previous-file");
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        request.get("/api/core/files/attachments/lectures/" + attachmentVideoUnit.getLecture().getId() + "/merge-pdf", HttpStatus.NOT_FOUND, byte[].class);
    }

    private AttachmentVideoUnit createAttachmentVideoUnitWithoutStudentVersion(byte[] content) throws IOException {
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(content);
        clearStoredStudentVersion(attachmentVideoUnit.getAttachment());
        return attachmentVideoUnit;
    }

    /**
     * Removes the stored student version. The reference is not updatable through the entity, so it is cleared with the conditional statement the application uses.
     */
    private void clearStoredStudentVersion(Attachment attachment) {
        String stored = attachmentRepo.findStudentVersionStateById(attachment.getId()).orElseThrow().studentVersion();
        if (stored != null) {
            assertThat(attachmentRepo.clearStudentVersionIfUnchanged(attachment.getId(), stored)).isEqualTo(1);
        }
        attachment.setStudentVersion(null);
    }

    private void saveSlide(AttachmentVideoUnit attachmentVideoUnit, int slideNumber, ZonedDateTime hidden, boolean superseded) {
        saveSlide(attachmentVideoUnit, slideNumber, hidden, superseded, attachmentVideoUnit.getAttachment().getVersion(), attachmentVideoUnit.getAttachment().getSha256Hash());
    }

    private void saveSlide(AttachmentVideoUnit attachmentVideoUnit, int slideNumber, ZonedDateTime hidden, boolean superseded, Integer splitFromVersion, String splitFromHash) {
        var slide = new Slide();
        slide.setAttachmentRevision(splitFromVersion, splitFromHash);
        slide.setAttachmentVideoUnit(attachmentVideoUnit);
        slide.setSlideNumber(slideNumber);
        slide.setSlideImagePath("attachments/attachment-unit/" + attachmentVideoUnit.getId() + "/slide/" + slideNumber + ".png");
        slide.setHidden(hidden);
        slide.setSuperseded(superseded);
        slideRepository.save(slide);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLectureAttachmentRangeRequest() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        Attachment attachment = createLectureAttachmentWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/lectures/" + attachment.getAttachmentVideoUnit().getLecture().getId() + "/" + attachment.getName() + ".pdf";

        MvcResult result = mockMvc.perform(get(url).header("Range", "bytes=2-5")).andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Accept-Ranges", "bytes")).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(new byte[] { 50, 51, 52, 53 });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetAttachmentVideoUnitAttachmentRangeRequestNotSatisfiable() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/dummy.pdf";

        mockMvc.perform(get(url).header("Range", "bytes=25-30")).andExpect(status().isRequestedRangeNotSatisfiable()).andExpect(header().string("Content-Range", "bytes */10"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetAttachmentVideoUnitAttachmentRangeRequestMalformedHeader() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/dummy.pdf";

        mockMvc.perform(get(url).header("Range", "bytes=abc-def")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testMarkdownFileCacheHeaders() throws Exception {
        // Upload a markdown file
        MockMultipartFile file = new MockMultipartFile("file", "test-image.png", "image/png", "test image content".getBytes());
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        JsonNode response = request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=false&courseId=" + course.getId(), file.getOriginalFilename(), "file", file,
                JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asString();

        // Verify cache headers (30 days = 2592000 seconds)
        mockMvc.perform(get(responsePath)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "max-age=2592000, public"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testExamUserSignatureCacheHeaders() throws Exception {
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course, 1);
        var user = new ExamUserDTO(TEST_PREFIX + "student1", null, null, null, null, null, "", "", true, true, true, true, null, null, null, null, null, null, null, null);
        var file = new MockMultipartFile("file", "signature.png", "image/png", "signature data".getBytes());

        ExamUser examUser = request.postWithMultipartFile("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/exam-users", user, "examUserDTO", file,
                ExamUser.class, HttpStatus.OK);
        String requestUrl = "%s%s".formatted(ARTEMIS_FILE_PATH_PREFIX, examUser.getSigningImagePath());

        // Verify cache headers (30 days = 2592000 seconds)
        mockMvc.perform(get(requestUrl)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "max-age=2592000, public"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersionCacheHeaders() throws Exception {
        byte[] dummyContent = "dummy pdf content".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        String expectedCacheControl = CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate().getHeaderValue();
        MvcResult response = mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, expectedCacheControl))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED)).andExpect(content().bytes(dummyContent)).andReturn();
        assertAuthenticationVaryHeader(response);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersionNotModified() throws Exception {
        byte[] dummyContent = "dummy pdf content".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        MvcResult result = mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.LAST_MODIFIED)).andReturn();
        String lastModified = result.getResponse().getHeader(HttpHeaders.LAST_MODIFIED);
        Mockito.clearInvocations(fileService);

        // A stale or explicitly revalidated cached response must not read the unchanged file again
        String expectedCacheControl = CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate().getHeaderValue();
        MvcResult notModifiedResponse = mockMvc.perform(get(url).header(HttpHeaders.IF_MODIFIED_SINCE, lastModified)).andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, expectedCacheControl)).andReturn();
        assertAuthenticationVaryHeader(notModifiedResponse);
        // Browsers copy the policy of a 304 onto the cached file, and a sandboxed PDF is not displayed
        assertThat(notModifiedResponse.getResponse().getHeader("Content-Security-Policy")).doesNotContain("sandbox");
        Mockito.verify(fileService, Mockito.never()).getFileForPath(storedAttachmentVideoUnitFile(attachmentVideoUnit.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLectureAttachmentCacheHeaders() throws Exception {
        byte[] dummyContent = "dummy pdf content".getBytes();
        Attachment attachment = createLectureAttachmentWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/lectures/" + attachment.getAttachmentVideoUnit().getLecture().getId() + "/" + attachment.getName() + ".pdf";

        String expectedCacheControl = CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate().getHeaderValue();
        MvcResult response = mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, expectedCacheControl))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED)).andReturn();
        assertAuthenticationVaryHeader(response);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersionRangeRequestWithMatchingIfRange() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        MvcResult fullResponse = mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.LAST_MODIFIED)).andReturn();
        String lastModified = fullResponse.getResponse().getHeader(HttpHeaders.LAST_MODIFIED);

        String expectedCacheControl = CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate().getHeaderValue();
        mockMvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=2-5").header(HttpHeaders.IF_RANGE, lastModified)).andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, expectedCacheControl)).andExpect(header().string(HttpHeaders.LAST_MODIFIED, lastModified))
                .andExpect(content().bytes("2345".getBytes()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersionRangeRequestWithStaleIfRange() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/student/dummy.pdf";

        MvcResult fullResponse = mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.LAST_MODIFIED)).andReturn();
        String lastModified = fullResponse.getResponse().getHeader(HttpHeaders.LAST_MODIFIED);
        String staleIfRange = ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME).minusSeconds(1).format(DateTimeFormatter.RFC_1123_DATE_TIME);

        // A stale validator makes the server ignore Range and return the complete current representation
        String expectedCacheControl = CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate().getHeaderValue();
        mockMvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=2-5").header(HttpHeaders.IF_RANGE, staleIfRange)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, expectedCacheControl)).andExpect(header().string(HttpHeaders.LAST_MODIFIED, lastModified))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE)).andExpect(content().bytes(dummyContent));
    }

    private static void assertAuthenticationVaryHeader(MvcResult result) {
        assertThat(String.join(",", result.getResponse().getHeaders(HttpHeaders.VARY))).contains(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE);
    }

    /**
     * An attachment video unit whose file really lies where the server looks for it, so that these tests exercise the production resolution instead of a stubbed one.
     *
     * @param content the bytes to store as the unit's attachment
     * @return the saved attachment video unit
     */
    private AttachmentVideoUnit createAttachmentVideoUnitWithStoredFile(byte[] content) throws IOException {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);
        lectureRepo.save(lecture);

        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        attachmentVideoUnit.setLecture(lecture);
        Attachment attachment = attachmentVideoUnit.getAttachment();
        attachment.setName("test-file");
        attachment.setLink(STORED_ATTACHMENT_FILENAME);
        attachmentRepo.save(attachment);
        FileUtils.writeByteArrayToFile(storedAttachmentVideoUnitFile(attachmentVideoUnit.getId()).toFile(), content);
        return attachmentVideoUnitRepo.save(attachmentVideoUnit);
    }

    /**
     * @param attachmentVideoUnitId the unit the file belongs to
     * @return where the unit's attachment lies on disk
     */
    private static Path storedAttachmentVideoUnitFile(long attachmentVideoUnitId) {
        return new FileSystemLocation.AttachmentVideoUnitFile(attachmentVideoUnitId, STORED_ATTACHMENT_FILENAME).path();
    }

    /**
     * The shape the migration in {@code 20260905235721_changelog.xml} leaves an attachment that used to hang off a lecture directly in: it belongs to an attachment video unit
     * now, and its file stayed under the directory of that unit's lecture. Nothing on the row says so, so the file really lies there and these tests exercise the production
     * resolution, including the transitional fallback in {@code FileSystemLocation.ofAttachment}, instead of a stubbed one.
     *
     * @param content the bytes to store as the attachment
     * @return the saved attachment
     */
    /**
     * Two attachments of one lecture may carry the same display name, and the legacy URL carries nothing else: it is
     * {@code {name}.{extension}}, so both answer to it. While the files still lay under the lecture directory the
     * candidates were narrowed to the ones stored there, which usually left one; the migration moves every file into
     * its unit's directory, so that filter is gone and the tie has to be broken deterministically instead. Without
     * that, the same old link can serve a different unit's file from one request to the next.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testLegacyLectureAttachmentRouteIsDeterministicForTwoAttachmentsOfTheSameName() throws Exception {
        byte[] olderContent = "the attachment the link was written for".getBytes();
        Attachment older = createLectureAttachmentWithStoredFile(olderContent);
        Lecture lecture = older.getAttachmentVideoUnit().getLecture();

        AttachmentVideoUnit secondUnit = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);
        Attachment sameName = LectureFactory.generateAttachment(ZonedDateTime.now());
        sameName.setName(older.getName());
        sameName.setAttachmentVideoUnit(secondUnit);
        sameName.setLink("second-" + STORED_ATTACHMENT_FILENAME);
        sameName = attachmentRepo.save(sameName);
        secondUnit.setAttachment(sameName);
        attachmentVideoUnitRepo.save(secondUnit);
        FileUtils.writeByteArrayToFile(new FileSystemLocation.AttachmentVideoUnitFile(secondUnit.getId(), "second-" + STORED_ATTACHMENT_FILENAME).path().toFile(),
                "the other unit's file".getBytes());

        assertThat(sameName.getId()).as("the second attachment really is the newer one").isGreaterThan(older.getId());

        String requestedName = older.getName() + ".pdf";
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(request.get("/api/core/files/attachments/lectures/" + lecture.getId() + "/" + requestedName, HttpStatus.OK, byte[].class))
                    .as("every request resolves to the same, older attachment").isEqualTo(olderContent);
        }
    }

    private Attachment createLectureAttachmentWithStoredFile(byte[] content) throws IOException {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);
        lecture = lectureRepo.save(lecture);

        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnitWithoutAttachment(lecture);

        Attachment attachment = LectureFactory.generateAttachment(ZonedDateTime.now().minusDays(1));
        attachment.setName("test-lecture-file");
        attachment.setAttachmentVideoUnit(attachmentVideoUnit);
        attachment.setLink(STORED_ATTACHMENT_FILENAME);
        attachment = attachmentRepo.save(attachment);
        attachmentVideoUnit.setAttachment(attachment);
        attachmentVideoUnitRepo.save(attachmentVideoUnit);
        FileUtils.writeByteArrayToFile(new FileSystemLocation.LectureAttachment(lecture.getId(), STORED_ATTACHMENT_FILENAME).path().toFile(), content);
        return attachment;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetDragAndDropBackgroundFile_questionWithoutBackground_notFound() throws Exception {
        var quiz = quizExerciseUtilService.createAndSaveEnrolledQuiz(TEST_PREFIX, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), QuizMode.INDIVIDUAL);
        var dndQuestion = quiz.getQuizQuestions().stream().filter(DragAndDropQuestion.class::isInstance).map(DragAndDropQuestion.class::cast).findFirst().orElseThrow();
        assertThat(dndQuestion.getBackgroundFilePath()).isNull();

        request.get("/api/core/files/drag-and-drop/questions/" + dndQuestion.getId() + "/backgrounds/background.png", HttpStatus.NOT_FOUND, byte[].class);
    }
}
