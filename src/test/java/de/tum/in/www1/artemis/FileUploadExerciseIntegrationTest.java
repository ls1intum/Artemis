package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class FileUploadExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private List<GradingCriterion> gradingCriteria;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadExerciseFails() throws Exception {
        String filePattern = "Example file pattern";
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(filePattern);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadExercise() throws Exception {
        String filePattern = "png, pdf, jPg      , r, DOCX";
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(filePattern);
        gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        FileUploadExercise receivedFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class);

        assertThat(receivedFileUploadExercise).isNotNull();
        assertThat(receivedFileUploadExercise.getId()).isNotNull();
        assertThat(receivedFileUploadExercise.getFilePattern()).isEqualTo(filePattern.toLowerCase().replaceAll("\\s+", ""));

        assertThat(receivedFileUploadExercise.getGradingCriteria().get(0).getTitle()).isEqualTo(null);
        assertThat(receivedFileUploadExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().size()).isEqualTo(1);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getFileUploadExercise() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);

        assertThat(fileUploadExercise.getId()).isEqualTo(receivedFileUploadExercise.getId());
        assertThat(fileUploadExercise).isEqualTo(receivedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteFileUploadExercise_asInstructor() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
        FileUploadExercise fileUploadExercise2 = (FileUploadExercise) exerciseRepo.findAll().get(1);

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        request.delete("/api/file-upload-exercises/" + fileUploadExercise2.getId(), HttpStatus.OK);

        assertThat(exerciseRepo.findAll().isEmpty());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteFileUploadExercise_asStudent() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
        FileUploadExercise fileUploadExercise2 = (FileUploadExercise) exerciseRepo.findAll().get(1);

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN);
        request.delete("/api/file-upload-exercises/" + fileUploadExercise2.getId(), HttpStatus.FORBIDDEN);

        assertThat(exerciseRepo.findAll().size() == 2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateFileUploadExercise_asInstructor() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
        fileUploadExercise.setDueDate(ZonedDateTime.now().plusDays(10));

        FileUploadExercise receivedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise,
                FileUploadExercise.class, HttpStatus.OK);
        assertThat(receivedFileUploadExercise.getDueDate().equals(ZonedDateTime.now().plusDays(10)));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllFileUploadExercisesForCourse_asInstructor() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        long courseID = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getId();

        List<FileUploadExercise> receivedFileUploadExercises = request.getList("/api/courses/" + courseID + "/file-upload-exercises", HttpStatus.OK, FileUploadExercise.class);

        // this seems to be a flaky test, based on the execution order, the following line has a problem with authentication, this should fix it
        database.changeUser("instructor1");
        assertThat(receivedFileUploadExercises.size() == courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getExercises().size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getAllFileUploadExercisesForCourse_asStudent() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        long courseID = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getId();

        List<FileUploadExercise> receivedFileUploadExercises = request.getList("/api/courses/" + courseID + "/file-upload-exercises", HttpStatus.FORBIDDEN,
                FileUploadExercise.class);

        assertThat(receivedFileUploadExercises).isNull();
    }
}
