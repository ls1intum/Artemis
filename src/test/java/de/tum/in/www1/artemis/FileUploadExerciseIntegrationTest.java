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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class FileUploadExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private List<GradingCriterion> gradingCriteria;

    private final String creationFilePattern = "png, pdf, jPg      , r, DOCX";

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
    public void createFileUploadExercise_InvalidMaxScore() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(10.0);
        fileUploadExercise.setBonusPoints(1.0);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadExercise_NotIncludedInvalidBonusPoints() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(10.0);
        fileUploadExercise.setBonusPoints(1.0);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        FileUploadExercise receivedFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class,
                HttpStatus.CREATED);

        assertThat(receivedFileUploadExercise).isNotNull();
        assertThat(receivedFileUploadExercise.getId()).isNotNull();
        assertThat(receivedFileUploadExercise.getFilePattern()).isEqualTo(creationFilePattern.toLowerCase().replaceAll("\\s+", ""));
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(receivedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("exerciseGroupId was set correctly")
                .isEqualTo(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId());

        assertThat(receivedFileUploadExercise.getGradingCriteria().get(0).getTitle()).isEqualTo(null);
        assertThat(receivedFileUploadExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().size()).isEqualTo(1);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);

        gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        FileUploadExercise createdFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);

        assertThat(createdFileUploadExercise).isNotNull();
        assertThat(createdFileUploadExercise.getId()).isNotNull();
        assertThat(createdFileUploadExercise.getFilePattern()).isEqualTo(creationFilePattern.toLowerCase().replaceAll("\\s+", ""));
        assertThat(!createdFileUploadExercise.isCourseExercise()).as("course was not set for exam exercise");
        assertThat(createdFileUploadExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(createdFileUploadExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());

        assertThat(createdFileUploadExercise.getGradingCriteria().get(0).getTitle()).isEqualTo(null);
        assertThat(createdFileUploadExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().size()).isEqualTo(1);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);
        fileUploadExercise.setCourse(fileUploadExercise.getExerciseGroup().getExam().getCourse());

        request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setCourse(null);
        fileUploadExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getFileUploadExercise() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);

        assertThat(fileUploadExercise.getId()).isEqualTo(receivedFileUploadExercise.getId());
        assertThat(fileUploadExercise).isEqualTo(receivedFileUploadExercise);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getExamFileUploadExercise_asStudent_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getExamFileUploadExercise_asTutor_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    private void getExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();
        request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void getExamFileUploadExercise_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);
        assertThat(receivedFileUploadExercise).as("exercise was retrieved").isNotNull();
        assertThat(receivedFileUploadExercise.getId()).as("exercise with the right id was retrieved").isEqualTo(fileUploadExercise.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteFileUploadExercise_asInstructor() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        for (var exercise : course.getExercises()) {
            request.delete("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.OK);
        }
        assertThat(exerciseRepo.findAll().isEmpty());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteFileUploadExercise_asStudent() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        for (var exercise : course.getExercises()) {
            request.delete("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        }

        assertThat(exerciseRepo.findAll().size() == course.getExercises().size());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void deleteExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        assertThat(exerciseRepo.findAll().isEmpty());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateFileUploadExercise_asInstructor() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        fileUploadExercise.setDueDate(ZonedDateTime.now().plusDays(10));

        FileUploadExercise receivedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise,
                FileUploadExercise.class, HttpStatus.OK);
        assertThat(receivedFileUploadExercise.getDueDate().equals(ZonedDateTime.now().plusDays(10)));
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(receivedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("courseId was not updated").isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateFileUploadExerciseForExam_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();
        String newTitle = "New file upload exercise title";
        fileUploadExercise.setTitle(newTitle);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise,
                FileUploadExercise.class, HttpStatus.OK);

        assertThat(updatedFileUploadExercise.getTitle().equals(newTitle));
        assertThat(!updatedFileUploadExercise.isCourseExercise()).as("course was not set for exam exercise");
        assertThat(updatedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedFileUploadExercise.getExerciseGroup().getId()).as("exerciseGroupId was not updated").isEqualTo(fileUploadExercise.getExerciseGroup().getId());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();
        fileUploadExercise.setCourse(fileUploadExercise.getExerciseGroup().getExam().getCourse());

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setExerciseGroup(null);
        fileUploadExercise.setCourse(null);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateFileUploadExercise_conversionBetweenCourseAndExamExercise_badRequest() throws Exception {
        FileUploadExercise fileUploadExerciseWithCourse = database.createFileUploadExercisesWithCourse().get(0);
        FileUploadExercise fileUploadExerciseWithExerciseGroup = database.addCourseExamExerciseGroupWithOneFileUploadExercise();

        fileUploadExerciseWithCourse.setCourse(null);
        fileUploadExerciseWithCourse.setExerciseGroup(fileUploadExerciseWithExerciseGroup.getExerciseGroup());

        fileUploadExerciseWithExerciseGroup.setCourse(fileUploadExerciseWithCourse.getCourseViaExerciseGroupOrCourseMember());
        fileUploadExerciseWithExerciseGroup.setExerciseGroup(null);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExerciseWithCourse.getId(), fileUploadExerciseWithCourse, FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExerciseWithExerciseGroup.getId(), fileUploadExerciseWithExerciseGroup, FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllFileUploadExercisesForCourse_asInstructor() throws Exception {
        database.addCourseWithThreeFileUploadExercise();
        long courseID = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getId();

        List<FileUploadExercise> receivedFileUploadExercises = request.getList("/api/courses/" + courseID + "/file-upload-exercises", HttpStatus.OK, FileUploadExercise.class);

        // this seems to be a flaky test, based on the execution order, the following line has a problem with authentication, this should fix it
        database.changeUser("instructor1");
        assertThat(receivedFileUploadExercises.size() == courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getExercises().size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getAllFileUploadExercisesForCourse_asStudent() throws Exception {
        database.addCourseWithThreeFileUploadExercise();
        long courseID = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getId();

        List<FileUploadExercise> receivedFileUploadExercises = request.getList("/api/courses/" + courseID + "/file-upload-exercises", HttpStatus.FORBIDDEN,
                FileUploadExercise.class);

        assertThat(receivedFileUploadExercises).isNull();
    }
}
