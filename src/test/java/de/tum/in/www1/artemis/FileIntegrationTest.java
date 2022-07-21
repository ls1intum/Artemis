package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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

class FileIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    private FileResource fileResource;

    @Autowired
    private LectureRepository lectureRepo;

    @BeforeEach
    void initTestCase() {
        database.addUsers(2, 2, 0, 1);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveTempFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=false", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();

        String responseFile = request.get(responsePath, HttpStatus.OK, String.class);
        assertThat(responseFile).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetFileUploadSubmission() throws Exception {
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
        String accessToken = request.get(fileUploadSubmission.getFilePath() + "/access-token", HttpStatus.OK, String.class);

        String receivedFile = request.get(fileUploadSubmission.getFilePath() + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedFile).isEqualTo("some data");
        request.get(fileUploadSubmission.getFilePath() + "?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetFileUploadSubmissionAsTutor() throws Exception {
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
        String accessToken = request.get(fileUploadSubmission.getFilePath() + "/access-token", HttpStatus.OK, String.class);

        String receivedFile = request.get(fileUploadSubmission.getFilePath() + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedFile).isEqualTo("some data");
        request.get(fileUploadSubmission.getFilePath() + "?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAccessTokenForFileUploadSubmission_InvalidIds() throws Exception {
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

        // invalid exercise id
        request.get("/api/files/file-upload-exercises/" + 999999999 + "/submissions/" + fileUploadSubmission.getId() + "/file.png/access-token", HttpStatus.NOT_FOUND,
                String.class);
        // invalid submission id
        request.get("/api/files/file-upload-exercises/" + fileUploadExercise.getId() + "/submissions/" + 999999999 + "/file.png/access-token", HttpStatus.NOT_FOUND, String.class);
        // invalid exercise and submission id
        request.get("/api/files/file-upload-exercises/" + 999999999 + "/submissions/" + 999999999 + "/file.png/access-token", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "other-ta1", roles = "TA")
    void testGetAccessTokenForFileUploadSubmissionAsTutorNotInCourse_forbidden() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");
        String path = "/api/files/file-upload-exercises/" + fileUploadExercise.getId() + "/submissions/" + fileUploadSubmission.getId() + "/test.png";
        fileUploadSubmission.setFilePath(path);

        // create tutor that is not in the course
        database.addTeachingAssistant("other-tutors", "other-ta");

        request.get(path + "/access-token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetAccessTokenForOwnFileUploadSubmissionAsStudent() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");
        String path = "/api/files/file-upload-exercises/" + fileUploadExercise.getId() + "/submissions/" + fileUploadSubmission.getId() + "/test.png";
        fileUploadSubmission.setFilePath(path);

        // get access token
        request.get(path + "/access-token", HttpStatus.OK, String.class);
    }

    @Test
    @WithMockUser(username = "student2")
    void testGetAccessTokenForOtherStudentsFileUploadSubmissionAsStudent_forbidden() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, "student1");
        String path = "/api/files/file-upload-exercises/" + fileUploadExercise.getId() + "/submissions/" + fileUploadSubmission.getId() + "/test.png";
        fileUploadSubmission.setFilePath(path);

        request.get(path + "/access-token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetLectureAttachment() throws Exception {
        Attachment attachment = createLectureWithAttachment("attachment.pdf", HttpStatus.CREATED);
        String attachmentPath = attachment.getLink();
        // get access token and then request the file using the access token
        String accessToken = request.get(attachmentPath + "/access-token", HttpStatus.OK, String.class);
        String receivedAttachment = request.get(attachmentPath + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");

        request.get(attachmentPath + "?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetUnreleasedLectureAttachmentAsTutor() throws Exception {
        Attachment attachment = createLectureWithAttachment("attachment.pdf", HttpStatus.CREATED);
        String attachmentPath = attachment.getLink();
        attachment.setReleaseDate(ZonedDateTime.now().plusDays(1));
        // get access token and then request the file using the access token
        String accessToken = request.get(attachmentPath + "/access-token", HttpStatus.OK, String.class);
        String receivedAttachment = request.get(attachmentPath + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetUnreleasedLectureAttachmentAsStudent_forbidden() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        lecture.setTitle("Test title");
        lecture.setDescription("Test");
        lecture.setStartDate(ZonedDateTime.now().minusHours(1));

        // generate attachment
        Attachment attachment = ModelFactory.generateAttachment(ZonedDateTime.now());
        attachment.setLecture(lecture);
        attachment.setReleaseDate(ZonedDateTime.now().plusDays(1));
        String attachmentPath = "/api/files/attachments/lecture/" + lecture.getId() + "/test.pdf";
        attachment.setLink(attachmentPath);

        lectureRepo.save(lecture);
        attachmentRepo.save(attachment);

        request.get(attachmentPath + "/access-token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetLectureAttachment_InvalidLectureId() throws Exception {
        String invalidLectureAttachmentPath = "/api/files/attachments/lecture/999999999/testfile.pdf";
        request.get(invalidLectureAttachmentPath + "/access-token", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetLectureAttachment_unsupportedFileType() throws Exception {
        // this should return Unsupported file type
        createLectureWithAttachment("attachment.abc", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetLectureAttachment_mimeType() throws Exception {
        // add a new file type to allow to check the mime type detection in FileResource with an exotic extension
        fileResource.addAllowedFileExtension("exotic");

        Attachment attachment = createLectureWithAttachment("attachment.exotic", HttpStatus.CREATED);
        String attachmentPath = attachment.getLink();
        // get access token and then request the file using the access token
        String accessToken = request.get(attachmentPath + "/access-token", HttpStatus.OK, String.class);
        String receivedAttachment = request.get(attachmentPath + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");

        fileResource.addRemoveFileExtension("exotic");
    }

    public Attachment createLectureWithAttachment(String filename, HttpStatus expectedStatus) throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAttachmentUnit() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);

        MockMultipartFile file = new MockMultipartFile("file", "filename2.png", "application/json", "some data".getBytes());
        AttachmentUnit attachmentUnit = uploadAttachmentUnit(file, lecture.getId(), HttpStatus.CREATED);
        database.addLectureUnitsToLecture(lecture, Set.of(attachmentUnit));

        String attachmentPath = attachmentUnit.getAttachment().getLink();
        // get access token and then request the file using the access token
        String accessToken = request.get(attachmentPath + "/access-token", HttpStatus.OK, String.class);
        String receivedAttachment = request.get(attachmentPath + "?access_token=" + accessToken, HttpStatus.OK, String.class);
        assertThat(receivedAttachment).isEqualTo("some data");

        request.get(attachmentPath + "?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetAccessTokenForUnreleasedAttachmentUnitAsStudent_forbidden() throws Exception {
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

        request.get(attachmentPath + "/access-token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
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

        String accessToken = request.get(attachmentPath + "/access-token", HttpStatus.OK, String.class);
        request.get(attachmentPath + "?access_token=" + accessToken, HttpStatus.OK, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAttachmentUnit_InvalidAttachmentUnitId() throws Exception {
        String invalidAttachmentUnitPath = "/api/files/attachments/attachment-unit/999999999/testfile.pdf";
        request.get(invalidAttachmentUnitPath + "/access-token", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetLectureAttachmentUnitAttachment_forbidden() throws Exception {
        AttachmentUnit attachmentUnit = createLectureWithAttachmentUnit();
        String filename = new File(attachmentUnit.getAttachment().getLink()).getName();
        String requestPath = "/api/files/attachments/attachment-unit/" + attachmentUnit.getId() + "/" + filename;

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("access_token", "random_non_valid_token");

        request.getFile(requestPath, HttpStatus.FORBIDDEN, params);
    }

    private AttachmentUnit createLectureWithAttachmentUnit() {
        Lecture lecture = database.createCourseWithLecture(true);

        AttachmentUnit attachmentUnit = database.createAttachmentUnit(true);
        lecture.addLectureUnit(attachmentUnit);

        lectureRepo.save(lecture);
        return attachmentUnit;
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void uploadImageMarkdownAsStudent_forbidden() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
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
    @WithMockUser(username = "tutor1", roles = "TA")
    void uploadFileMarkdownUnsupportedFileExtensionAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.txt", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/markdown-file-upload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void uploadFileAsStudentForbidden() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "TA")
    void uploadFileAsTutor() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "application/json", "some data".getBytes());
        // upload file
        JsonNode response = request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.CREATED);
        String responsePath = response.get("path").asText();
        assertThat(responsePath).contains("temp");
    }

    @Test
    @WithMockUser(username = "student1", roles = "TA")
    void uploadFileUnsupportedFileExtension() throws Exception {
        // create file
        MockMultipartFile file = new MockMultipartFile("file", "image.txt", "application/json", "some data".getBytes());
        // upload file
        request.postWithMultipartFile("/api/fileUpload?keepFileName=true", file.getOriginalFilename(), "file", file, JsonNode.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetLecturePdfAttachmentsMerged_InvalidToken() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        request.get("/api/files/attachments/lecture/" + lecture.getId() + "/merge-pdf?access_token=random_non_valid_token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetLecturePdfAttachmentsMerged_InvalidCourseId() throws Exception {
        request.get("/api/files/attachments/course/" + 199999999 + "/access-token", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetLecturePdfAttachmentsMerged_InvalidLectureId() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();

        // get access token and then send request using the access token
        String accessToken = request.get("/api/files/attachments/course/" + lecture.getCourse().getId() + "/access-token", HttpStatus.OK, String.class);
        request.get("/api/files/attachments/lecture/" + 999999999 + "/merge-pdf" + "?access_token=" + accessToken, HttpStatus.NOT_FOUND, byte[].class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void testGetLecturePdfAttachmentsMerged_StudentNotRegisteredInCourse() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        request.get("/api/files/attachments/course/" + lecture.getCourse().getId() + "/access-token", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetLecturePdfAttachmentsMerged() throws Exception {
        Lecture lecture = createLectureWithLectureUnits();
        callAndCheckMergeResult(lecture, 5);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetLecturePdfAttachmentsMerged_TutorAccessToUnreleasedUnits() throws Exception {
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

    private Lecture createLectureWithLectureUnits() throws Exception {
        return createLectureWithLectureUnits(HttpStatus.CREATED);
    }

    private Lecture createLectureWithLectureUnits(HttpStatus expectedStatus) throws Exception {
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

        String attachmentPath = fileService.manageFilesForUpdatedFilePath(null, responsePath, targetFolder, attachmentUnit.getId(), true);
        attachmentUnit.getAttachment().setLink(attachmentPath);
        attachmentRepo.save(attachmentUnit.getAttachment());

        return attachmentUnit;
    }

}
