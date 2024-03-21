package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.ExamUserDTO;

class FileIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "fileintegration";

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepo;

    @Autowired
    private LectureUnitCompletionRepository lectureUnitCompletionRepository;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUploadExamUserSignature() throws Exception {
        var course = courseUtilService.addEmptyCourse();
        var exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course, 1);
        var user = new ExamUserDTO(TEST_PREFIX + "student1", null, null, null, null, null, "", "", true, true, true, true, null);
        var file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());

        ExamUser updateExamUserResponse = request.postWithMultipartFile("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/exam-users", user, "examUserDTO", file,
                ExamUser.class, HttpStatus.OK);
        byte[] getUserSignatureResponse = request.get(updateExamUserResponse.getSigningImagePath(), HttpStatus.OK, byte[].class);

        assertThat(getUserSignatureResponse).isEqualTo(file.getBytes());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetTemplateFile() throws Exception {
        String javaReadme = request.get("/api/files/templates/JAVA/PLAIN_MAVEN", HttpStatus.OK, String.class);
        assertThat(javaReadme).isNotEmpty();
        String cReadme = request.get("/api/files/templates/C/GCC", HttpStatus.OK, String.class);
        assertThat(cReadme).isNotEmpty();
        String pythonReadme = request.get("/api/files/templates/PYTHON", HttpStatus.OK, String.class);
        assertThat(pythonReadme).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCodeOfConductTemplate() throws Exception {
        var template = request.get("/api/files/templates/code-of-conduct", HttpStatus.OK, String.class);
        assertThat(template).startsWith("<!-- Code of Conduct Template");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetUnreleasedAttachmentUnitAsTutor() throws Exception {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        lecture.setTitle("Test title");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));

        // create unreleased attachment unit
        AttachmentUnit attachmentUnit = lectureUtilService.createAttachmentUnit(true);
        attachmentUnit.setLecture(lecture);
        Attachment attachment = attachmentUnit.getAttachment();
        attachment.setReleaseDate(ZonedDateTime.now().plusDays(1));

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);
        attachmentUnit = attachmentUnitRepo.save(attachmentUnit);

        request.get(attachmentUnit.getAttachment().getLink(), HttpStatus.OK, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void uploadImageMarkdownAsStudent_forbidden() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void uploadImageMarkdownAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class,
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
        request.postWithMultipartFile("/api/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetLecturePdfAttachmentsMerged_InvalidLectureId() throws Exception {
        request.get("/api/files/attachments/lecture/" + 999999999 + "/merge-pdf", HttpStatus.NOT_FOUND, byte[].class);
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
        List<LectureUnit> expectedCompletedUnits = List.of(units.get(0), units.get(2));
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
        var attachment = lecture.getLectureUnits().stream().sorted(Comparator.comparing(LectureUnit::getId)).map(lectureUnit -> ((AttachmentUnit) lectureUnit).getAttachment())
                .findFirst().orElseThrow();
        attachment.setReleaseDate(ZonedDateTime.now().plusHours(2));
        attachmentRepo.save(attachment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLecturePdfAttachmentsMerged_correctOrder() throws Exception {
        // The test setup needs elevated privileges, we later switch to a student for the test execution
        Lecture lecture = createLectureWithLectureUnits();

        // Change order of units
        LectureUnit unit3 = lecture.getLectureUnits().get(2);
        lecture.getLectureUnits().remove(unit3);
        lecture.getLectureUnits().add(0, unit3);
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
        byte[] receivedFile = request.get("/api/files/attachments/lecture/" + lecture.getId() + "/merge-pdf", HttpStatus.OK, byte[].class);

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
            lecture.getLectureUnits().add(uploadAttachmentUnit(lecture, file1, expectedStatus));
        }

        // create image file
        MockMultipartFile file2 = new MockMultipartFile("file", "filename2.png", "application/json", "some text".getBytes());
        lecture.getLectureUnits().add(uploadAttachmentUnit(lecture, file2, expectedStatus));

        // create pdf file 3
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc2 = new PDDocument()) {
            // Add first page with extra cropBox to make it distinguishable in the later tests
            PDPage page = new PDPage();
            page.setCropBox(new PDRectangle(1, 2, 3, 4));
            doc2.addPage(page);
            doc2.addPage(new PDPage());
            doc2.save(outputStream);
            MockMultipartFile file3 = new MockMultipartFile("file", "filename3.pdf", "application/json", outputStream.toByteArray());
            lecture.getLectureUnits().add(uploadAttachmentUnit(lecture, file3, expectedStatus));
        }

        // Collect units freshly from the database to prevent issues when persisting the lecture again
        lecture.setLectureUnits(attachmentUnitRepo.findAllByLectureIdAndAttachmentType(lecture.getId(), AttachmentType.FILE).stream().map(unit -> (LectureUnit) unit)
                .collect(Collectors.toCollection(ArrayList::new)));

        return lecture;
    }

    private AttachmentUnit uploadAttachmentUnit(Lecture lecture, MockMultipartFile file, HttpStatus expectedStatus) throws Exception {
        AttachmentUnit attachmentUnit = LectureFactory.generateAttachmentUnit();
        Attachment attachment = attachmentUnit.getAttachment();
        attachmentUnit.setAttachment(null);
        attachment.setAttachmentUnit(null);
        MockMultipartFile attachmentFile = new MockMultipartFile("attachment", "", "application/json", objectMapper.writeValueAsBytes(attachment));

        return request.postWithMultipartFiles("/api/lectures/" + lecture.getId() + "/attachment-units", attachmentUnit, "attachmentUnit", List.of(attachmentFile, file),
                AttachmentUnit.class, expectedStatus);
    }

}
