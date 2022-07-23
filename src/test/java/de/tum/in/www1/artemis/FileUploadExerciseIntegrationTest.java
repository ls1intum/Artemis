package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.in.www1.artemis.util.ModelFactory;

class FileUploadExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    private List<GradingCriterion> gradingCriteria;

    private final String creationFilePattern = "png, pdf, jPg      , r, DOCX";

    @BeforeEach
    void initTestCase() {
        database.addUsers(2, 1, 0, 1);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails() throws Exception {
        String filePattern = "Example file pattern";
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(filePattern);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFailsIfAlreadyCreated() throws Exception {
        String filePattern = "Example file pattern";
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(filePattern);
        fileUploadExercise = fileUploadExerciseRepository.save(fileUploadExercise);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_InvalidMaxScore() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_InvalidInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        // make sure the instructor is not instructor for this course anymore by changing the courses' instructor group name
        var course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        fileUploadExercise.setFilePattern(creationFilePattern);
        gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails_AlmostEmptyFilePattern() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(" ");
        gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails_EmptyFilePattern() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern("");
        gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(10.0);
        fileUploadExercise.setBonusPoints(1.0);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_NotIncludedInvalidBonusPoints() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(10.0);
        fileUploadExercise.setBonusPoints(1.0);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise() throws Exception {
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

        assertThat(receivedFileUploadExercise.getGradingCriteria().get(0).getTitle()).isNull();
        assertThat(receivedFileUploadExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions()).hasSize(1);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);

        gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        FileUploadExercise createdFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);

        assertThat(createdFileUploadExercise).isNotNull();
        assertThat(createdFileUploadExercise.getId()).isNotNull();
        assertThat(createdFileUploadExercise.getFilePattern()).isEqualTo(creationFilePattern.toLowerCase().replaceAll("\\s+", ""));
        assertThat(createdFileUploadExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(createdFileUploadExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(createdFileUploadExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());

        assertThat(createdFileUploadExercise.getGradingCriteria().get(0).getTitle()).isNull();
        assertThat(createdFileUploadExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions()).hasSize(1);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);

        request.postWithResponseBody("/api/file-upload-exercises", invalidDates.applyTo(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
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
    void getFileUploadExercise() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);

        assertThat(fileUploadExercise.getId()).isEqualTo(receivedFileUploadExercise.getId());
        assertThat(fileUploadExercise).isEqualTo(receivedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getExamFileUploadExercise_asStudent_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getExamFileUploadExercise_asTutor_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    private void getExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();
        request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getExamFileUploadExercise_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);
        assertThat(receivedFileUploadExercise).as("exercise was retrieved").isNotNull();
        assertThat(receivedFileUploadExercise.getId()).as("exercise with the right id was retrieved").isEqualTo(fileUploadExercise.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getFileUploadExerciseFails_wrongId() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        request.get("/api/file-upload-exercises/" + 555555, HttpStatus.NOT_FOUND, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getExamFileUploadExercise_InstructorNotInGroup() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        for (var exercise : course.getExercises()) {
            request.get("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetFileUploadExercise_setGradingInstructionFeedbackUsed() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(gradingCriteria.get(0).getStructuredGradingInstructions().get(0));
        feedbackRepository.save(feedback);

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);

        assertThat(receivedFileUploadExercise.isGradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExercise_asInstructor() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        for (var exercise : course.getExercises()) {
            request.delete("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.OK);
        }
        assertThat(exerciseRepo.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void deleteFileUploadExercise_asStudent() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        for (var exercise : course.getExercises()) {
            request.delete("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        }

        assertThat(exerciseRepo.findAll()).hasSize(course.getExercises().size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExerciseFails_WithWrongId() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        request.delete("/api/file-upload-exercises/" + 5555555, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExerciseFails_InstructorNotInGroup() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        for (var exercise : course.getExercises()) {
            request.delete("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        }
        assertThat(exerciseRepo.findAll()).hasSize(3);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        assertThat(exerciseRepo.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_asInstructor() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        final ZonedDateTime dueDate = ZonedDateTime.now().plusDays(10);
        fileUploadExercise.setDueDate(dueDate);
        fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(11));

        FileUploadExercise receivedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId() + "?notificationText=notification",
                fileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        assertThat(receivedFileUploadExercise.getDueDate()).isEqualToIgnoringNanos(dueDate);
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(receivedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("courseId was not updated").isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseFails_InstructorNotInGroup() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        fileUploadExercise.setDueDate(ZonedDateTime.now().plusDays(10));
        fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(11));
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        FileUploadExercise receivedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise,
                FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();
        String newTitle = "New file upload exercise title";
        fileUploadExercise.setTitle(newTitle);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise,
                FileUploadExercise.class, HttpStatus.OK);

        assertThat(updatedFileUploadExercise.getTitle()).isEqualTo(newTitle);
        assertThat(updatedFileUploadExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(updatedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedFileUploadExercise.getExerciseGroup().getId()).as("exerciseGroupId was not updated").isEqualTo(fileUploadExercise.getExerciseGroup().getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_invalid_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), dates.applyTo(fileUploadExercise), FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        FileUploadExercise fileUploadExercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();
        fileUploadExercise.setCourse(fileUploadExercise.getExerciseGroup().getExam().getCourse());

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setExerciseGroup(null);
        fileUploadExercise.setCourse(null);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_conversionBetweenCourseAndExamExercise_badRequest() throws Exception {
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
    void updateModelingExerciseDueDate() throws Exception {
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise = fileUploadExerciseRepository.save(fileUploadExercise);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final FileUploadSubmission submission1 = ModelFactory.generateFileUploadSubmission(true);
            database.addFileUploadSubmission(fileUploadExercise, submission1, "student1");
            final FileUploadSubmission submission2 = ModelFactory.generateFileUploadSubmission(true);
            database.addFileUploadSubmission(fileUploadExercise, submission2, "student2");

            final var participations = studentParticipationRepository.findByExerciseId(fileUploadExercise.getId());
            assertThat(participations).hasSize(2);
            participations.get(0).setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            studentParticipationRepository.saveAll(participations);
        }

        fileUploadExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        request.put("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, HttpStatus.OK);

        {
            final var participations = studentParticipationRepository.findByExerciseId(fileUploadExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(individualDueDate);
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAllFileUploadExercisesForCourse_asInstructor() throws Exception {
        database.addCourseWithThreeFileUploadExercise();
        long courseID = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getId();

        List<FileUploadExercise> receivedFileUploadExercises = request.getList("/api/courses/" + courseID + "/file-upload-exercises", HttpStatus.OK, FileUploadExercise.class);

        // this seems to be a flaky test, based on the execution order, the following line has a problem with authentication, this should fix it
        database.changeUser("instructor1");
        assertThat(receivedFileUploadExercises).hasSize(courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getExercises().size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAllFileUploadExercisesForCourseFails_InstructorNotInGroup() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        long courseID = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getId();
        request.getList("/api/courses/" + courseID + "/file-upload-exercises", HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getAllFileUploadExercisesForCourse_asStudent() throws Exception {
        database.addCourseWithThreeFileUploadExercise();
        long courseID = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).get(0).getId();

        List<FileUploadExercise> receivedFileUploadExercises = request.getList("/api/courses/" + courseID + "/file-upload-exercises", HttpStatus.FORBIDDEN,
                FileUploadExercise.class);

        assertThat(receivedFileUploadExercises).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        database.addAssessmentWithFeedbackWithGradingInstructionsForExercise(fileUploadExercise, "instructor1");

        // change grading instruction score
        gradingCriteria.get(0).getStructuredGradingInstructions().get(0).setCredits(3);
        gradingCriteria.remove(1);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody(
                "/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", fileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        List<Result> updatedResults = database.getResultsForExercise(updatedFileUploadExercise);
        assertThat(updatedFileUploadExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0).getCredits()).isEqualTo(3);
        assertThat(updatedResults.get(0).getScore()).isEqualTo(60);
        assertThat(updatedResults.get(0).getFeedbacks().get(0).getCredits()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_shouldDeleteFeedbacks() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        database.addAssessmentWithFeedbackWithGradingInstructionsForExercise(fileUploadExercise, "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.remove(1);
        gradingCriteria.remove(0);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody(
                "/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate" + "?deleteFeedback=true", fileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        List<Result> updatedResults = database.getResultsForExercise(updatedFileUploadExercise);
        assertThat(updatedFileUploadExercise.getGradingCriteria()).hasSize(1);
        assertThat(updatedResults.get(0).getScore()).isZero();
        assertThat(updatedResults.get(0).getFeedbacks()).isEmpty();

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        course.setInstructorGroupName("test");
        courseRepo.save(course);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate", fileUploadExercise, FileUploadExercise.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadExercise fileUploadExerciseToBeConflicted = fileUploadExerciseRepository.findByIdElseThrow(fileUploadExercise.getId());
        fileUploadExerciseToBeConflicted.setId(123456789L);
        fileUploadExerciseRepository.save(fileUploadExerciseToBeConflicted);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate", fileUploadExerciseToBeConflicted, FileUploadExercise.class,
                HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_notFound() throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");

        request.putWithResponseBody("/api/file-upload-exercises/" + 123456789 + "/re-evaluate", fileUploadExercise, FileUploadExercise.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = database.addCourseWithFileUploadExercise();
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        fileUploadExercise.setId(null);
        fileUploadExercise.setAssessmentDueDate(null);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(3));
        fileUploadExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(3));
        fileUploadExercise.setDueDate(null);
        fileUploadExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = database.addCourseWithFileUploadExercise();
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        fileUploadExercise.setId(null);
        fileUploadExercise.setAssessmentDueDate(null);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        fileUploadExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);

        var result = request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        fileUploadExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);

        result = request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetFileUploadExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetFileUploadExercise_exampleSolutionVisibility(true, "student1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetFileUploadExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetFileUploadExercise_exampleSolutionVisibility(false, "instructor1");
    }

    private void testGetFileUploadExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        Course course = database.addCourseWithThreeFileUploadExercise();
        final FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        // Utility function to avoid duplication
        Function<Course, FileUploadExercise> fileUploadExerciseGetter = c -> (FileUploadExercise) c.getExercises().stream()
                .filter(e -> e.getId().equals(fileUploadExercise.getId())).findAny().get();

        fileUploadExercise.setExampleSolution("Sample<br>solution");

        if (isStudent) {
            database.createAndSaveParticipationForExercise(fileUploadExercise, username);
        }

        // Test example solution publication date not set.
        fileUploadExercise.setExampleSolutionPublicationDate(null);
        fileUploadExerciseRepository.save(fileUploadExercise);

        course = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK, Course.class);
        FileUploadExercise fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());
        }

        // Test example solution publication date in the past.
        fileUploadExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        fileUploadExerciseRepository.save(fileUploadExercise);

        course = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK, Course.class);
        fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());

        // Test example solution publication date in the future.
        fileUploadExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        fileUploadExerciseRepository.save(fileUploadExercise);

        course = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK, Course.class);
        fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());
        }
    }
}
