package de.tum.cit.aet.artemis.core;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
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
    private LectureUtilService lectureUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IrisRequestMockProvider irisRequestMockProvider;

    @BeforeEach
    void initTestCase() {
        irisRequestMockProvider.enableMockingOfRequests();
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
        }, ExpectedCount.manyTimes());

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    @AfterEach
    void tearDown() throws Exception {
        irisRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetTemplateFile() throws Exception {
        String javaReadme = request.get("/api/core/files/templates/JAVA/PLAIN_MAVEN", HttpStatus.OK, String.class);
        assertThat(javaReadme).isNotEmpty();
        String cReadme = request.get("/api/core/files/templates/C/GCC", HttpStatus.OK, String.class);
        assertThat(cReadme).isNotEmpty();
        String pythonReadme = request.get("/api/core/files/templates/PYTHON", HttpStatus.OK, String.class);
        assertThat(pythonReadme).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCodeOfConductTemplate() throws Exception {
        var template = request.get("/api/core/files/templates/code-of-conduct", HttpStatus.OK, String.class);
        assertThat(template).startsWith("<!-- Code of Conduct Template");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
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
     * This endpoint is mapped under two spellings of its path and resolves the file from the stored link rather than from the request, so a lecture attachment has to come back
     * under either one. Post markdown and client caches keep asking for whichever spelling they recorded, which is what keeps both mappings load-bearing.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLectureAttachmentUnderEitherPathSpelling() throws Exception {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);
        lecture = lectureRepo.save(lecture);

        Attachment attachment = LectureFactory.generateAttachmentWithFile(ZonedDateTime.now(), lecture.getId(), false);
        attachment.setLecture(lecture);
        // The attachment is served under the canonical spelling; the older one stays reachable for the request paths recorded in post markdown and client caches.
        assertThat(attachment.getLink()).startsWith("attachments/lectures/" + lecture.getId() + "/");
        attachmentRepo.save(attachment);

        String requestedName = attachment.getName() + ".jpg";
        assertThat(request.get("/api/core/files/attachments/lecture/" + lecture.getId() + "/" + requestedName, HttpStatus.OK, byte[].class)).isNotEmpty();
        assertThat(request.get("/api/core/files/attachments/lectures/" + lecture.getId() + "/" + requestedName, HttpStatus.OK, byte[].class)).isNotEmpty();
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
     * A post written before this release embeds everything after {@code attachments/} of the attachment link the server was serving at the time, and the client re-expands that
     * fragment against {@code api/core/files/attachments/}. Those posts are user-authored prose in the database that no migration reaches, so the singular spelling they carry
     * has to keep resolving for as long as the posts exist. This walks the two fragments the editor records for an attachment video unit reference, the attachment itself and
     * its student version.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
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
        // upload file
        request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void uploadImageMarkdownAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class,
                HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        assertThat(responsePath).contains("markdown");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void uploadFileMarkdownUnsupportedFileExtensionAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.txt", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetLecturePdfAttachmentsMerged_InvalidLectureId() throws Exception {
        request.get("/api/core/files/attachments/lectures/" + 999999999 + "/merge-pdf", HttpStatus.NOT_FOUND, byte[].class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLecturePdfAttachmentsMerged_TutorAccessToUnreleasedUnits() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();

        adjustReleaseDateToFuture(lecture);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        // The unit is hidden but a tutor can still see it
        // -> the merged result should contain the unit
        callAndCheckMergeResult(lecture, 5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAttachmentFileAsEditor() throws Exception {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);

        Attachment attachment = LectureFactory.generateAttachmentWithFile(ZonedDateTime.now(), lecture.getId(), false);
        attachment.setLecture(lecture);

        Long courseId = lecture.getCourse().getId();

        lectureRepo.save(lecture);
        attachment = attachmentRepo.save(attachment);
        Long attachmentId = attachment.getId();

        request.get("/api/core/files/courses/" + courseId + "/attachments/" + attachmentId, HttpStatus.OK, byte[].class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAttachmentVideoUnitStudentVersion() throws Exception {
        testGetAttachmentVideoUnitAsStudent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
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

        JsonNode response = request.postWithMultipartFile("/api/core/files/courses/" + course.getId() + "/conversations/" + conversation.getId(), file.getOriginalFilename(),
                "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
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
    void testGetLectureAttachmentRangeRequest() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        Attachment attachment = createLectureAttachmentWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/lectures/" + attachment.getLecture().getId() + "/" + attachment.getName() + ".pdf";

        MvcResult result = mockMvc.perform(get(url).header("Range", "bytes=2-5")).andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Accept-Ranges", "bytes")).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(new byte[] { 50, 51, 52, 53 });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAttachmentVideoUnitAttachmentRangeRequestNotSatisfiable() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/dummy.pdf";

        mockMvc.perform(get(url).header("Range", "bytes=25-30")).andExpect(status().isRequestedRangeNotSatisfiable()).andExpect(header().string("Content-Range", "bytes */10"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAttachmentVideoUnitAttachmentRangeRequestMalformedHeader() throws Exception {
        byte[] dummyContent = "0123456789".getBytes();
        AttachmentVideoUnit attachmentVideoUnit = createAttachmentVideoUnitWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/attachment-video-units/" + attachmentVideoUnit.getId() + "/dummy.pdf";

        mockMvc.perform(get(url).header("Range", "bytes=abc-def")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testMarkdownFileCacheHeaders() throws Exception {
        // Upload a markdown file
        MockMultipartFile file = new MockMultipartFile("file", "test-image.png", "image/png", "test image content".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/core/markdown-file-upload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class,
                HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

        // Verify cache headers (30 days = 2592000 seconds)
        mockMvc.perform(get(responsePath)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "max-age=2592000, public"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
        Mockito.verify(fileService, Mockito.never()).getFileForPath(storedAttachmentVideoUnitFile(attachmentVideoUnit.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLectureAttachmentCacheHeaders() throws Exception {
        byte[] dummyContent = "dummy pdf content".getBytes();
        Attachment attachment = createLectureAttachmentWithStoredFile(dummyContent);
        String url = "/api/core/files/attachments/lectures/" + attachment.getLecture().getId() + "/" + attachment.getName() + ".pdf";

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
     * A lecture attachment whose file really lies where the server looks for it.
     *
     * @param content the bytes to store as the attachment
     * @return the saved attachment
     */
    private Attachment createLectureAttachmentWithStoredFile(byte[] content) throws IOException {
        Lecture lecture = lectureUtilService.createEnrolledCourseWithLecture(TEST_PREFIX, true);
        lectureRepo.save(lecture);

        Attachment attachment = LectureFactory.generateAttachment(ZonedDateTime.now().minusDays(1));
        attachment.setName("test-lecture-file");
        attachment.setLecture(lecture);
        attachment.setLink(STORED_ATTACHMENT_FILENAME);
        attachment = attachmentRepo.save(attachment);
        FileUtils.writeByteArrayToFile(new FileSystemLocation.LectureAttachment(lecture.getId(), STORED_ATTACHMENT_FILENAME).path().toFile(), content);
        return attachment;
    }

}
