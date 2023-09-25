package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.DragItem;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.fileuploadexercise.FileUploadExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

class FileIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "fileintegration";

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

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
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
        String javaReadme = request.get("/api/files/templates/JAVA/PLAIN_MAVEN", HttpStatus.OK, String.class);
        assertThat(javaReadme).isNotEmpty();
        String cReadme = request.get("/api/files/templates/C/GCC", HttpStatus.OK, String.class);
        assertThat(cReadme).isNotEmpty();
        String pythonReadme = request.get("/api/files/templates/PYTHON", HttpStatus.OK, String.class);
        assertThat(pythonReadme).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseIcon() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "application/json", "some data".getBytes());
        Course savedCourse = request.putWithMultipartFiles("/api/courses/" + course.getId(), course, "course", List.of(file), Course.class, HttpStatus.OK, null);

        String receivedIcon = request.get(savedCourse.getCourseIcon(), HttpStatus.OK, String.class);
        assertThat(receivedIcon).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetDragAndDropBackgroundFile() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now(), null, QuizMode.SYNCHRONIZED);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        quizExerciseRepository.save(quizExercise);

        MockMultipartFile file = new MockMultipartFile("file", "background.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

        dragAndDropQuestion.setBackgroundFilePath(responsePath);
        dragAndDropQuestion = quizQuestionRepository.save(dragAndDropQuestion);

        String receivedPath = request.get(dragAndDropQuestion.getBackgroundFilePath(), HttpStatus.OK, String.class);
        assertThat(receivedPath).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetDragItemFile() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now(), null, QuizMode.SYNCHRONIZED);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        quizExerciseRepository.save(quizExercise);

        DragItem dragItem = dragAndDropQuestion.getDragItems().get(0);
        MockMultipartFile file = new MockMultipartFile("file", "background.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

        dragItem.setPictureFilePath(responsePath);
        dragAndDropQuestion = quizQuestionRepository.save(dragAndDropQuestion);
        dragItem = dragAndDropQuestion.getDragItems().get(0);

        String receivedPath = request.get(dragItem.getPictureFilePath(), HttpStatus.OK, String.class);
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
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1");
        MockMultipartFile file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());

        userUtilService.changeUser(TEST_PREFIX + "student1");
        FileUploadSubmission savedSubmission = request.postWithMultipartFiles("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", fileUploadSubmission,
                "submission", List.of(file), FileUploadSubmission.class, HttpStatus.OK);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        return savedSubmission;
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
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        lecture.setTitle("Test title");
        lecture.setDescription("Test");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));

        Attachment attachment = LectureFactory.generateAttachment(ZonedDateTime.now());
        attachment.setLecture(lecture);

        User currentUser = userRepository.getUser();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // create file
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/json", "some data".getBytes());
        Attachment createdAttachment = request.postWithMultipartFile("/api/attachments", attachment, "attachment", file, Attachment.class, expectedStatus);
        if (expectedStatus != HttpStatus.CREATED) {
            return null;
        }
        lecture.addAttachments(createdAttachment);
        userUtilService.changeUser(currentUser.getLogin());

        lectureRepo.save(lecture);
        return createdAttachment;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAttachmentUnit() throws Exception {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);

        MockMultipartFile file = new MockMultipartFile("file", "filename2.png", "application/json", "some data".getBytes());
        AttachmentUnit attachmentUnit = uploadAttachmentUnit(lecture, file, HttpStatus.CREATED);

        String attachmentPath = attachmentUnit.getAttachment().getLink();
        String receivedAttachment = request.get(attachmentPath, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLecturePdfAttachmentsMerged() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        userUtilService.changeUser(TEST_PREFIX + "student1");
        callAndCheckMergeResult(lecture, 5);
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
        AttachmentUnit attachmentUnit = LectureFactory.generateAttachmentUnit(false);
        Attachment attachment = attachmentUnit.getAttachment();
        attachmentUnit.setAttachment(null);
        attachment.setAttachmentUnit(null);
        MockMultipartFile attachmentFile = new MockMultipartFile("attachment", "", "application/json", objectMapper.writeValueAsBytes(attachment));

        return request.postWithMultipartFiles("/api/lectures/" + lecture.getId() + "/attachment-units", attachmentUnit, "attachmentUnit", List.of(attachmentFile, file),
                AttachmentUnit.class, expectedStatus);
    }

}
