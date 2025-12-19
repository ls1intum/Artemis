package de.tum.cit.aet.artemis.core;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
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
        var course = courseUtilService.addEmptyCourse();
        var exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course, 1);
        var user = new ExamUserDTO(TEST_PREFIX + "student1", null, null, null, null, null, "", "", true, true, true, true, null, null, null, null, null, null, null, null);
        var file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());

        ExamUser updateExamUserResponse = request.postWithMultipartFile("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/exam-users", user, "examUserDTO", file,
                ExamUser.class, HttpStatus.OK);
        String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, updateExamUserResponse.getSigningImagePath());
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
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        lecture.setTitle("Test title");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));

        // create unreleased attachment video unit
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        attachmentVideoUnit.setLecture(lecture);
        attachmentVideoUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));

        lectureRepo.save(lecture);
        attachmentVideoUnit = attachmentVideoUnitRepo.save(attachmentVideoUnit);

        String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, attachmentVideoUnit.getAttachment().getLink());
        request.get(requestUrl, HttpStatus.OK, String.class);
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
        request.get("/api/core/files/attachments/lecture/" + 999999999 + "/merge-pdf", HttpStatus.NOT_FOUND, byte[].class);
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
        byte[] receivedFile = request.get("/api/core/files/attachments/lecture/" + lecture.getId() + "/merge-pdf", HttpStatus.OK, byte[].class);

        assertThat(receivedFile).isNotEmpty();
        return Loader.loadPDF(receivedFile);
    }

    private Lecture createLectureWithLectureUnits() throws Exception {
        return createLectureWithLectureUnits(HttpStatus.CREATED);
    }

    private Lecture createLectureWithLectureUnits(HttpStatus expectedStatus) throws Exception {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);

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
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);

        Attachment attachment = LectureFactory.generateAttachmentWithFile(ZonedDateTime.now(), lecture.getId(), false);
        attachment.setId(1L);
        attachment.setLecture(lecture);

        Long courseId = lecture.getCourse().getId();
        Long attachmentId = attachment.getId();

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);

        request.get("/api/core/files/courses/" + courseId + "/attachments/" + attachmentId, HttpStatus.OK, byte[].class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAttachmentVideoUnitFileAsEditor() throws Exception {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);

        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        attachmentVideoUnit.setLecture(lecture);
        Attachment attachment = attachmentVideoUnit.getAttachment();

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);
        attachmentVideoUnitRepo.save(attachmentVideoUnit);

        Long courseId = lecture.getCourse().getId();
        Long attachmentVideoUnitId = attachmentVideoUnit.getId();

        request.get("/api/core/files/courses/" + courseId + "/attachment-units/" + attachmentVideoUnitId, HttpStatus.OK, byte[].class);
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
        Path tempFile = Files.createTempFile(tempPath, "dummy", ".pdf");
        byte[] dummyContent = "dummy pdf content".getBytes();
        FileUtils.writeByteArrayToFile(tempFile.toFile(), dummyContent);
        tempFile.toFile().deleteOnExit();

        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        lectureRepo.save(lecture);

        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(lecture, true);
        attachmentVideoUnit.setLecture(lecture);

        String unsanitizedName = "test–file"; // contains en-dash
        Attachment attachment = attachmentVideoUnit.getAttachment();
        attachment.setName(unsanitizedName);
        attachment.setLink(tempFile.toUri().toString());
        attachmentRepo.save(attachment);
        attachmentVideoUnitRepo.save(attachmentVideoUnit);

        String unsanitizedFilename = "AttachmentUnit_2025-05-10T12-10-34_" + unsanitizedName + ".pdf";
        String url = isTutor ? "/api/core/files/attachments/attachment-unit/" + attachmentVideoUnit.getId() + "/" + unsanitizedFilename
                : "/api/core/files/attachments/attachment-unit/" + attachmentVideoUnit.getId() + "/student/" + unsanitizedFilename;

        try (MockedStatic<FilePathConverter> filePathServiceMock = Mockito.mockStatic(FilePathConverter.class)) {
            filePathServiceMock.when(() -> FilePathConverter.fileSystemPathForExternalUri(Mockito.any(URI.class), Mockito.eq(FilePathType.ATTACHMENT_UNIT))).thenReturn(tempFile);

            MvcResult result = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();

            byte[] responseContent = result.getResponse().getContentAsByteArray();
            assertThat(responseContent).isEqualTo(dummyContent);

            String contentDisposition = result.getResponse().getHeader("Content-Disposition");
            assertThat(contentDisposition).isNotNull();
            assertThat(contentDisposition).doesNotContain("–");
            assertThat(contentDisposition).contains("filename=\"test_file.pdf\"");
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUploadAndRetrieveFileForConversation() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);
        var posts = conversationUtilService.createPostsWithinCourse(courseUtilService.createCourse(), TEST_PREFIX);
        var conversation = posts.getFirst().getConversation();
        var course = conversation.getCourse();

        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[] { 1, 2, 3, 4, 5 });

        JsonNode response = request.postWithMultipartFile("/api/core/files/courses/" + course.getId() + "/conversations/" + conversation.getId(), file.getOriginalFilename(),
                "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

        byte[] retrievedContent = request.get(responsePath, HttpStatus.OK, byte[].class);
        assertThat(retrievedContent).isEqualTo(file.getBytes());
    }

}
