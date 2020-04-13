package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.DragItem;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.FileResource;

public class FileIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    AttachmentRepository attachmentRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    QuizExerciseRepository quizExerciseRepository;

    @Autowired
    QuizQuestionRepository quizQuestionRepository;

    @Autowired
    FileService fileService;

    @Autowired
    FileResource fileResource;

    @Autowired
    ParticipationService participationService;

    @Autowired
    LectureRepository lectureRepo;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testSaveTempFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

        String responseFile = request.get(responsePath, HttpStatus.OK, String.class);
        assertThat(responseFile).isEqualTo("some data");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetTemplateFile() throws Exception {
        String javaReadme = request.get("/api/files/templates/JAVA/readme", HttpStatus.OK, String.class);
        assertThat(javaReadme).isNotEmpty();
        String cReadme = request.get("/api/files/templates/C/readme", HttpStatus.OK, String.class);
        assertThat(cReadme).isNotEmpty();
        String pythonReadme = request.get("/api/files/templates/PYTHON/readme", HttpStatus.OK, String.class);
        assertThat(pythonReadme).isNotEmpty();

        request.get("/api/files/templates/randomnonexistingfile", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetCourseIcon() throws Exception {
        Course course = database.addEmptyCourse();
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String iconPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, Constants.COURSE_ICON_FILEPATH, course.getId());

        course.setCourseIcon(iconPath);
        courseRepo.save(course);

        String receivedIcon = request.get(iconPath, HttpStatus.OK, String.class);
        assertThat(receivedIcon).isEqualTo("some data");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetDragAndDropBackgroundFile() throws Exception {
        Course course = database.addEmptyCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), null);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        quizExerciseRepository.save(quizExercise);

        MockMultipartFile file = new MockMultipartFile("file", "background.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String backgroundPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH, dragAndDropQuestion.getId());

        dragAndDropQuestion.setBackgroundFilePath(backgroundPath);
        courseRepo.save(course);
        quizQuestionRepository.save(dragAndDropQuestion);

        String receivedPath = request.get(backgroundPath, HttpStatus.OK, String.class);
        assertThat(receivedPath).isEqualTo("some data");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetDragItemFile() throws Exception {
        Course course = database.addEmptyCourse();
        QuizExercise quizExercise = database.createQuiz(course, ZonedDateTime.now(), null);
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        quizExerciseRepository.save(quizExercise);

        DragItem dragItem = dragAndDropQuestion.getDragItems().get(0);
        MockMultipartFile file = new MockMultipartFile("file", "background.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String dragItemPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, Constants.DRAG_ITEM_FILEPATH, dragItem.getId());

        dragItem.setPictureFilePath(dragItemPath);
        courseRepo.save(course);
        quizQuestionRepository.save(dragAndDropQuestion);

        String receivedPath = request.get(dragItemPath, HttpStatus.OK, String.class);
        assertThat(receivedPath).isEqualTo("some data");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetFileUploadSubmission() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");

        MockMultipartFile file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String filePath = fileService.manageFilesForUpdatedFilePath(null, responsePath,
                fileUploadSubmission.buildFilePath(fileUploadExercise.getId(), fileUploadSubmission.getId()), fileUploadSubmission.getId(), true);

        fileUploadSubmission.setFilePath(filePath);

        // get access token
        String accessToken = request.get("/api/files/attachments/access-token/file.png", HttpStatus.OK, String.class);

        String receivedFile = request.get(fileUploadSubmission.getFilePath() + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedFile).isEqualTo("some data");
        request.get(fileUploadSubmission.getFilePath() + "?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetLectureAttachment_unsupportedFileType() throws Exception {
        // this should return Unsupported file type
        createLectureWithAttachment("attachment.abc", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
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

        Attachment attachment = ModelFactory.generateAttachment(ZonedDateTime.now(), lecture);

        // create file
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/json", "some data".getBytes());
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, expectedStatus);
        if (expectedStatus != HttpStatus.CREATED) {
            return null;
        }
        String responsePath = response.get("path").asText();
        // move file from temp folder to correct folder
        String attachmentPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, Constants.LECTURE_ATTACHMENT_FILEPATH + lecture.getId() + '/', lecture.getId(), true);

        attachment.setLink(attachmentPath);
        lecture.addAttachments(attachment);

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);
        return attachmentPath;
    }
}
