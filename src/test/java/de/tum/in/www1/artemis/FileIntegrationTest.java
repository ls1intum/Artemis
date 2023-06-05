package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.DragItem;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.util.ModelFactory;

class FileIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "fileintegration";

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepo;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private LectureRepository lectureRepo;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 2, 2, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveTempFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

        String responseFile = request.get(responsePath, HttpStatus.OK, String.class);
        assertThat(responseFile).isEqualTo("some data");
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseIcon() throws Exception {
        Course course = database.addEmptyCourse();
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String iconPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, FilePathService.getCourseIconFilePath(), course.getId());

        course.setCourseIcon(iconPath);
        courseRepo.save(course);

        String receivedIcon = request.get(iconPath, HttpStatus.OK, String.class);
        assertThat(receivedIcon).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetDragAndDropBackgroundFile() throws Exception {
        Course course = database.addEmptyCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), null, QuizMode.SYNCHRONIZED);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        quizExerciseRepository.save(quizExercise);

        MockMultipartFile file = new MockMultipartFile("file", "background.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String backgroundPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, FilePathService.getDragAndDropBackgroundFilePath(), dragAndDropQuestion.getId());

        dragAndDropQuestion.setBackgroundFilePath(backgroundPath);
        courseRepo.save(course);
        quizQuestionRepository.save(dragAndDropQuestion);

        String receivedPath = request.get(backgroundPath, HttpStatus.OK, String.class);
        assertThat(receivedPath).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetDragItemFile() throws Exception {
        Course course = database.addEmptyCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), null, QuizMode.SYNCHRONIZED);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        quizExerciseRepository.save(quizExercise);

        DragItem dragItem = dragAndDropQuestion.getDragItems().get(0);
        MockMultipartFile file = new MockMultipartFile("file", "background.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String dragItemPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, FilePathService.getDragItemFilePath(), dragItem.getId());

        dragItem.setPictureFilePath(dragItemPath);
        courseRepo.save(course);
        quizQuestionRepository.save(dragAndDropQuestion);

        String receivedPath = request.get(dragItemPath, HttpStatus.OK, String.class);
        assertThat(receivedPath).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFileUploadSubmission() throws Exception {
        FileUploadSubmission fileUploadSubmission = createFileUploadSubmissionWithRealFile();
        String receivedFile = request.get(fileUploadSubmission.getFilePath(), HttpStatus.OK, String.class);
        assertThat(receivedFile).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFileUploadSubmissionAsTutor() throws Exception {
        FileUploadSubmission fileUploadSubmission = createFileUploadSubmissionWithRealFile();

        String receivedFile = request.get(fileUploadSubmission.getFilePath(), HttpStatus.OK, String.class);
        assertThat(receivedFile).isEqualTo("some data");
    }

    private FileUploadSubmission createFileUploadSubmissionWithRealFile() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1");

        MockMultipartFile file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String filePath = fileService.manageFilesForUpdatedFilePath(null, responsePath,
                FileUploadSubmission.buildFilePath(fileUploadExercise.getId(), fileUploadSubmission.getId()), fileUploadSubmission.getId(), true);

        fileUploadSubmission.setFilePath(filePath);
        return fileUploadSubmission;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLectureAttachment() throws Exception {
        Attachment attachment = createLectureWithAttachment("attachment.pdf", HttpStatus.CREATED);
        String attachmentPath = attachment.getLink();
        String receivedAttachment = request.get(attachmentPath, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetUnreleasedLectureAttachmentAsTutor() throws Exception {
        Attachment attachment = createLectureWithAttachment("attachment.pdf", HttpStatus.CREATED);
        String attachmentPath = attachment.getLink();
        attachment.setReleaseDate(ZonedDateTime.now().plusDays(1));
        String receivedAttachment = request.get(attachmentPath, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLectureAttachment_unsupportedFileType() throws Exception {
        // this should return Unsupported file type
        createLectureWithAttachment("attachment.abc", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLectureAttachment_mimeType() throws Exception {
        Attachment attachment = createLectureWithAttachment("attachment.svg", HttpStatus.CREATED);
        String attachmentPath = attachment.getLink();
        String receivedAttachment = request.get(attachmentPath, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");
    }

    private Attachment createLectureWithAttachment(String filename, HttpStatus expectedStatus) throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        lecture.setTitle("Test title");
        lecture.setDescription("Test");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));

        Attachment attachment = ModelFactory.generateAttachment(ZonedDateTime.now());
        attachment.setLecture(lecture);

        // create file
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/json", "some data".getBytes());
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, expectedStatus);
        if (expectedStatus != HttpStatus.CREATED) {
            return null;
        }
        String responsePath = response.get("path").asText();
        // move file from temp folder to correct folder
        var targetFolder = Path.of(FilePathService.getLectureAttachmentFilePath(), String.valueOf(lecture.getId())).toString();
        String attachmentPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, targetFolder, lecture.getId(), true);

        attachment.setLink(attachmentPath);
        lecture.addAttachments(attachment);

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);
        return attachment;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAttachmentUnit() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);

        MockMultipartFile file = new MockMultipartFile("file", "filename2.png", "application/json", "some data".getBytes());
        AttachmentUnit attachmentUnit = uploadAttachmentUnit(file, HttpStatus.CREATED);
        database.addLectureUnitsToLecture(lecture, List.of(attachmentUnit));

        String attachmentPath = attachmentUnit.getAttachment().getLink();
        String receivedAttachment = request.get(attachmentPath, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetUnreleasedAttachmentUnitAsTutor() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        lecture.setTitle("Test title");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));

        // create unreleased attachment unit
        AttachmentUnit attachmentUnit = database.createAttachmentUnit(true);
        attachmentUnit.setLecture(lecture);
        Attachment attachment = attachmentUnit.getAttachment();
        attachment.setReleaseDate(ZonedDateTime.now().plusDays(1));
        String attachmentPath = attachment.getLink();

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);
        attachmentUnitRepo.save(attachmentUnit);

        request.get(attachmentPath, HttpStatus.OK, String.class);
    }

    private AttachmentUnit createLectureWithAttachmentUnit() {
        Lecture lecture = database.createCourseWithLecture(true);

        AttachmentUnit attachmentUnit = database.createAttachmentUnit(true);
        lecture.addLectureUnit(attachmentUnit);

        lectureRepo.save(lecture);
        return attachmentUnit;
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void uploadFileAsStudentForbidden() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "TA")
    void uploadFileAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        assertThat(responsePath).contains("temp");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void uploadFileUnsupportedFileExtension() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "something.exotic", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.BAD_REQUEST);
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
        database.changeUser(TEST_PREFIX + "student1");

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
        database.changeUser(TEST_PREFIX + "student1");

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
        return createLectureWithLectureUnits(HttpStatus.CREATED);
    }

    private Lecture createLectureWithLectureUnits(HttpStatus expectedStatus) throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);

        lecture.setTitle("Test title");
        lecture.setDescription("Test");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));
        lectureRepo.save(lecture);

        // create pdf file 1
        AttachmentUnit unit1;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc1 = new PDDocument()) {
            doc1.addPage(new PDPage());
            doc1.addPage(new PDPage());
            doc1.addPage(new PDPage());
            doc1.save(outputStream);
            MockMultipartFile file1 = new MockMultipartFile("file", "file.pdf", "application/json", outputStream.toByteArray());
            unit1 = uploadAttachmentUnit(file1, expectedStatus);
        }

        // create image file
        MockMultipartFile file2 = new MockMultipartFile("file", "filename2.png", "application/json", "some text".getBytes());
        AttachmentUnit unit2 = uploadAttachmentUnit(file2, expectedStatus);

        // create pdf file 3
        AttachmentUnit unit3;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc2 = new PDDocument()) {
            // Add first page with extra cropBox to make it distinguishable in the later tests
            PDPage page = new PDPage();
            page.setCropBox(new PDRectangle(1, 2, 3, 4));
            doc2.addPage(page);
            doc2.addPage(new PDPage());
            doc2.save(outputStream);
            MockMultipartFile file3 = new MockMultipartFile("file", "filename3.pdf", "application/json", outputStream.toByteArray());
            unit3 = uploadAttachmentUnit(file3, expectedStatus);
        }

        lecture = database.addLectureUnitsToLecture(lecture, List.of(unit1, unit2, unit3));

        return lecture;
    }

    private AttachmentUnit uploadAttachmentUnit(MockMultipartFile file, HttpStatus expectedStatus) throws Exception {

        AttachmentUnit attachmentUnit = database.createAttachmentUnit(false);

        // upload file
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, expectedStatus);
        if (expectedStatus != HttpStatus.CREATED) {
            return null;
        }

        String responsePath = response.get("path").asText();

        // move file from temp folder to correct folder
        var targetFolder = Path.of(FilePathService.getAttachmentUnitFilePath(), String.valueOf(attachmentUnit.getId())).toString();

        String attachmentPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, targetFolder, attachmentUnit.getId(), true);
        attachmentUnit.getAttachment().setLink(attachmentPath);
        attachmentRepo.save(attachmentUnit.getAttachment());

        return attachmentUnit;
    }

}
