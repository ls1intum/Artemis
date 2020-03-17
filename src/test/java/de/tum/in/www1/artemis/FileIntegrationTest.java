package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.DragItem;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.util.GitUtilService;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FileUploadSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;

public class FileIntegrationTest extends AbstractSpringIntegrationTest {

    public static final String API_FILE_UPLOAD_SUBMISSIONS = "/api/file-upload-submissions/";

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    AttachmentRepository attachmentRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    QuizExerciseRepository quizExerciseRepository;

    @Autowired
    QuizQuestionRepository quizQuestionRepository;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    FileService fileService;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ParticipationService participationService;

    @Autowired
    LectureRepository lectureRepo;

    @Autowired
    private BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

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
        Course course = database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = (ProgrammingExercise) new ArrayList<>(course.getExercises()).get(0);

        programmingExerciseService.setupProgrammingExercise(programmingExercise);

        request.get("/files/templates/" + programmingExercise.getProgrammingLanguage().toString().toLowerCase() + "/exercise", HttpStatus.OK, byte[].class);
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
        String filePath = fileService.manageFilesForUpdatedFilePath(null, responsePath, fileUploadSubmission.buildFilePath(fileUploadExercise.getId(), fileUploadSubmission.getId()), fileUploadSubmission.getId(), true);

        fileUploadSubmission.setFilePath(filePath);

        //get access token
        String accessToken = request.get("/api/files/attachments/access-token/file.png", HttpStatus.OK, String.class);

        String receivedFile = request.get(fileUploadSubmission.getFilePath() + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedFile).isEqualTo("some data");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetLectureAttachment() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        MockMultipartFile file = new MockMultipartFile("file", "attachment.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        String attachmentPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, Constants.LECTURE_ATTACHMENT_FILEPATH, lecture.getId(), true);

        Attachment attachment = new Attachment();
        attachment.setLink(attachmentPath);
        attachmentRepo.save(attachment);
        lecture.addAttachments(attachment);
        lectureRepo.save(lecture);

        //get access token
        String accessToken = request.get("/api/files/attachments/access-token/attachment.png", HttpStatus.OK, String.class);

        String receivedAttachment = request.get(attachmentPath + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");
    }
}
