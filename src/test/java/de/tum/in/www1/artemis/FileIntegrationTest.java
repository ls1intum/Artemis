package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
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
import de.tum.in.www1.artemis.web.rest.FileResource;

public class FileIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private FileResource fileResource;

    @Autowired
    private LectureRepository lectureRepo;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 0, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveTempFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

        String responseFile = request.get(responsePath, HttpStatus.OK, String.class);
        assertThat(responseFile).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetTemplateFile() throws Exception {
        String javaReadme = request.get("/api/files/templates/JAVA/PLAIN_MAVEN/readme", HttpStatus.OK, String.class);
        assertThat(javaReadme).isNotEmpty();
        String cReadme = request.get("/api/files/templates/C/GCC/readme", HttpStatus.OK, String.class);
        assertThat(cReadme).isNotEmpty();
        String pythonReadme = request.get("/api/files/templates/PYTHON/readme", HttpStatus.OK, String.class);
        assertThat(pythonReadme).isNotEmpty();

        request.get("/api/files/templates/randomnonexistingfile", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetCourseIcon() throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetDragAndDropBackgroundFile() throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetDragItemFile() throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetFileUploadSubmission() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");

        MockMultipartFile file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String filePath = fileService.manageFilesForUpdatedFilePath(null, responsePath,
                FileUploadSubmission.buildFilePath(fileUploadExercise.getId(), fileUploadSubmission.getId()), fileUploadSubmission.getId(), true);

        fileUploadSubmission.setFilePath(filePath);

        // get access token
        String accessToken = request.get("/api/files/attachments/access-token/file.png", HttpStatus.OK, String.class);

        String receivedFile = request.get(fileUploadSubmission.getFilePath() + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedFile).isEqualTo("some data");
        request.get(fileUploadSubmission.getFilePath() + "?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetLectureAttachment() throws Exception {
        String filename = "attachment.pdf";
        String attachmentPath = createLectureWithAttachment(filename, HttpStatus.CREATED);
        // get access token and then request the file using the access token
        String accessToken = request.get("/api/files/attachments/access-token/" + filename, HttpStatus.OK, String.class);
        String receivedAttachment = request.get(attachmentPath + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");

        request.get(attachmentPath + "?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetLectureAttachment_unsupportedFileType() throws Exception {
        // this should return Unsupported file type
        createLectureWithAttachment("attachment.abc", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetLectureAttachment_mimeType() throws Exception {
        // add a new file type to allow to check the mime type detection in FileResource with an exotic extension
        fileResource.addAllowedFileExtension("exotic");

        String filename = "attachment.exotic";
        String attachmentPath = createLectureWithAttachment(filename, HttpStatus.CREATED);
        // get access token and then request the file using the access token
        String accessToken = request.get("/api/files/attachments/access-token/" + filename, HttpStatus.OK, String.class);
        String receivedAttachment = request.get(attachmentPath + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");

        fileResource.addRemoveFileExtension("exotic");
    }

    public String createLectureWithAttachment(String filename, HttpStatus expectedStatus) throws Exception {
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
        return attachmentPath;
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void uploadImageMarkdownAsStudent_forbidden() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void uploadImageMarkdownAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class,
                HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        assertThat(responsePath).contains("markdown");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void uploadFileMarkdownUnsupportedFileExtensionAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.txt", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void uploadFileAsStudentForbidden() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "TA")
    public void uploadFileAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        assertThat(responsePath).contains("temp");
    }

    @Test
    @WithMockUser(username = "student1", roles = "TA")
    public void uploadFileUnsupportedFileExtension() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.txt", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetLecturePdfAttachmentsMerged_InvalidToken() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        request.get("/api/files/attachments/lecture/" + lecture.getId() + "/merge-pdf?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetLecturePdfAttachmentsMerged_InvalidCourseId() throws Exception {
        request.get("/api/files/attachments/course/" + 199999999 + "/access-token", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetLecturePdfAttachmentsMerged_InvalidLectureId() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();

        // get access token and then send request using the access token
        String accessToken = request.get("/api/files/attachments/course/" + lecture.getCourse().getId() + "/access-token", HttpStatus.OK, String.class);
        request.get("/api/files/attachments/lecture/" + 999999999 + "/merge-pdf" + "?access_token=" + accessToken, HttpStatus.NOT_FOUND, byte[].class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetLecturePdfAttachmentsMerged_StudentNotRegisteredInCourse() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        request.get("/api/files/attachments/course/" + lecture.getCourse().getId() + "/access-token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetLecturePdfAttachmentsMerged() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        callAndCheckMergeResult(lecture, 5);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetLecturePdfAttachmentsMerged_TutorAccessToUnreleasedUnits() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();

        var attachment = lecture.getLectureUnits().stream().sorted(Comparator.comparing(LectureUnit::getId)).map(lectureUnit -> ((AttachmentUnit) lectureUnit).getAttachment())
                .findFirst().orElseThrow();
        attachment.setReleaseDate(ZonedDateTime.now().plusHours(2));
        attachmentRepo.save(attachment);

        // The unit is hidden but a tutor can still see it
        // -> the merged result should contain the unit
        callAndCheckMergeResult(lecture, 5);
    }

    private void callAndCheckMergeResult(Lecture lecture, int expectedPages) throws Exception {
        // get access token and then send request using the access token
        String accessToken = request.get("/api/files/attachments/course/" + lecture.getCourse().getId() + "/access-token", HttpStatus.OK, String.class);
        byte[] receivedFile = request.get("/api/files/attachments/lecture/" + lecture.getId() + "/merge-pdf" + "?access_token=" + accessToken, HttpStatus.OK, byte[].class);

        assertThat(receivedFile).isNotEmpty();
        try (PDDocument mergedDoc = PDDocument.load(receivedFile)) {
            assertEquals(expectedPages, mergedDoc.getNumberOfPages());
        }
    }

    public Lecture createLectureWithLectureUnits() throws Exception {
        return createLectureWithLectureUnits(HttpStatus.CREATED);
    }

    public Lecture createLectureWithLectureUnits(HttpStatus expectedStatus) throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);

        lecture.setTitle("Test title");
        lecture.setDescription("Test");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));
        lectureRepo.save(lecture);

        Long lectureId = lecture.getId();

        // create pdf file 1
        AttachmentUnit unit1;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc1 = new PDDocument()) {
            doc1.addPage(new PDPage());
            doc1.addPage(new PDPage());
            doc1.addPage(new PDPage());
            doc1.save(outputStream);
            MockMultipartFile file1 = new MockMultipartFile("file", "file.pdf", "application/json", outputStream.toByteArray());
            unit1 = uploadAttachmentUnit(file1, lectureId, expectedStatus);
        }

        // create image file
        MockMultipartFile file2 = new MockMultipartFile("file", "filename2.png", "application/json", "some text".getBytes());
        AttachmentUnit unit2 = uploadAttachmentUnit(file2, lectureId, expectedStatus);

        // create pdf file 3
        AttachmentUnit unit3;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc2 = new PDDocument()) {
            doc2.addPage(new PDPage());
            doc2.addPage(new PDPage());
            doc2.save(outputStream);
            MockMultipartFile file3 = new MockMultipartFile("file", "filename3.pdf", "application/json", outputStream.toByteArray());
            unit3 = uploadAttachmentUnit(file3, lectureId, expectedStatus);
        }

        lecture = database.addLectureUnitsToLecture(lecture, Set.of(unit1, unit2, unit3));

        return lecture;
    }

    private AttachmentUnit uploadAttachmentUnit(MockMultipartFile file, Long lectureId, HttpStatus expectedStatus) throws Exception {
        Lecture lecture = lectureRepo.findByIdWithLectureUnits(lectureId).get();

        AttachmentUnit attachmentUnit = database.createAttachmentUnit(false);

        // upload file
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, expectedStatus);
        if (expectedStatus != HttpStatus.CREATED) {
            return null;
        }

        String responsePath = response.get("path").asText();

        // move file from temp folder to correct folder
        var targetFolder = Path.of(FilePathService.getAttachmentUnitFilePath(), String.valueOf(attachmentUnit.getId())).toString();

        fileService.manageFilesForUpdatedFilePath(null, responsePath, targetFolder, lecture.getId(), true);
        var attachmentPath = targetFolder + "/" + file.getOriginalFilename();
        attachmentUnit.getAttachment().setLink(attachmentPath);
        attachmentRepo.save(attachmentUnit.getAttachment());

        return attachmentUnit;
    }

}
