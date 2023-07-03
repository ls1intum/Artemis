package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;
import java.util.Comparator;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

class FileIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "fileintegration";

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetTemplateFile() throws Exception {
        String javaReadme = request.get("/api/files/templates/JAVA/PLAIN_MAVEN/readme", HttpStatus.OK, String.class);
        assertThat(javaReadme).isNotEmpty();
        String cReadme = request.get("/api/files/templates/C/GCC/readme", HttpStatus.OK, String.class);
        assertThat(cReadme).isNotEmpty();
        String pythonReadme = request.get("/api/files/templates/PYTHON/readme", HttpStatus.OK, String.class);
        assertThat(pythonReadme).isNotEmpty();

        request.get("/api/files/templates/randomnonexistingfile", HttpStatus.NOT_FOUND, String.class);
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
        String attachmentPath = attachment.getLink();

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);
        attachmentUnitRepo.save(attachmentUnit);

        request.get(attachmentPath, HttpStatus.OK, String.class);
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
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetLecturePdfAttachmentsMerged() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        callAndCheckMergeResult(lecture, 5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLecturePdfAttachmentsMerged_TutorAccessToUnreleasedUnits() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();

        adjustReleaseDateToFuture(lecture);

        // The unit is hidden but a tutor can still see it
        // -> the merged result should contain the unit
        callAndCheckMergeResult(lecture, 5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLecturePdfAttachmentsMerged_NoAccessToUnreleasedUnits() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();

        adjustReleaseDateToFuture(lecture);

        // The test setup needs at least TA right for creating a lecture with files.
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLecturePdfAttachmentsMerged_correctOrder() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();

        // Change order of units
        LectureUnit unit3 = lecture.getLectureUnits().get(2);
        lecture.getLectureUnits().remove(unit3);
        lecture.getLectureUnits().add(0, unit3);
        lectureRepo.save(lecture);

        // The test setup needs at least TA right for creating a lecture with files.
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
        return PDDocument.load(receivedFile);
    }

    private Lecture createLectureWithLectureUnits() throws Exception {
        String userLogin = userRepository.getUser().getLogin();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

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
            uploadAttachmentUnit(lecture.getId(), file1);
        }

        // create image file
        MockMultipartFile file2 = new MockMultipartFile("file", "filename2.png", "application/json", "some text".getBytes());
        uploadAttachmentUnit(lecture.getId(), file2);

        // create pdf file 3
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc2 = new PDDocument()) {
            // Add first page with extra cropBox to make it distinguishable in the later tests
            PDPage page = new PDPage();
            page.setCropBox(new PDRectangle(1, 2, 3, 4));
            doc2.addPage(page);
            doc2.addPage(new PDPage());
            doc2.save(outputStream);
            MockMultipartFile file3 = new MockMultipartFile("file", "filename3.pdf", "application/json", outputStream.toByteArray());
            uploadAttachmentUnit(lecture.getId(), file3);
        }

        userUtilService.changeUser(userLogin);

        return lectureRepo.findByIdWithLectureUnitsElseThrow(lecture.getId());
    }

    private void uploadAttachmentUnit(Long lectureId, MockMultipartFile file) throws Exception {
        var attachmentUnit = new AttachmentUnit();
        var attachment = LectureFactory.generateAttachment(null);
        var attachmentUnitPart = new MockMultipartFile("attachmentUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());

        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lectures/" + lectureId + "/attachment-units").file(file).file(attachmentUnitPart).file(attachmentPart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        var result = request.getMvc().perform(builder).andExpect(status().is(HttpStatus.CREATED.value())).andReturn();

        mapper.readValue(result.getResponse().getContentAsString(), AttachmentUnit.class);
    }

}
