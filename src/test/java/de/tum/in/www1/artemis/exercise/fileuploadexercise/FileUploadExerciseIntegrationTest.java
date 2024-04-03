package de.tum.in.www1.artemis.exercise.fileuploadexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.GradingCriterionUtil;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CourseForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class FileUploadExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "fileuploaderxercise";

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

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    private Set<GradingCriterion> gradingCriteria;

    private final String creationFilePattern = "png, pdf, jPg , r, DOCX";

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails() throws Exception {
        String filePattern = "Example file pattern";
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(filePattern);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFailsIfAlreadyCreated() throws Exception {
        String filePattern = "Example file pattern";
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(filePattern);
        fileUploadExercise = fileUploadExerciseRepository.save(fileUploadExercise);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_InvalidMaxScore() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_InvalidInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        // make sure the instructor is not instructor for this course anymore by changing the courses' instructor group name
        var course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        fileUploadExercise.setFilePattern(creationFilePattern);
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails_AlmostEmptyFilePattern() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(" ");
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails_EmptyFilePattern() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern("");
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(10.0);
        fileUploadExercise.setBonusPoints(1.0);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_NotIncludedInvalidBonusPoints() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setMaxPoints(10.0);
        fileUploadExercise.setBonusPoints(1.0);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "exercise-new-fileupload-exerci", "" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise(String channelName) throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        courseUtilService.enableMessagingForCourse(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember());
        fileUploadExercise.setFilePattern(creationFilePattern);
        fileUploadExercise.setTitle("new fileupload exercise");
        fileUploadExercise.setChannelName(channelName);
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        FileUploadExercise receivedFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class,
                HttpStatus.CREATED);

        Channel channelFromDB = channelRepository.findChannelByExerciseId(receivedFileUploadExercise.getId());

        assertThat(receivedFileUploadExercise).isNotNull();
        assertThat(receivedFileUploadExercise.getId()).isNotNull();
        assertThat(receivedFileUploadExercise.getFilePattern()).isEqualTo(creationFilePattern.toLowerCase().replaceAll("\\s+", ""));
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(receivedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("exerciseGroupId was set correctly")
                .isEqualTo(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId());

        GradingCriterion criterionWithoutTitle = GradingCriterionUtil.findGradingCriterionByTitle(receivedFileUploadExercise, null);
        assertThat(criterionWithoutTitle.getStructuredGradingInstructions()).hasSize(1);
        assertThat(criterionWithoutTitle.getStructuredGradingInstructions().stream().findFirst().orElseThrow().getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");

        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getName()).isEqualTo("exercise-new-fileupload-exerci");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);

        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        FileUploadExercise createdFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);

        Channel channelFromDB = channelRepository.findChannelByExerciseId(createdFileUploadExercise.getId());
        assertThat(channelFromDB).isNull(); // there should not be any channel for exam exercise

        assertThat(createdFileUploadExercise).isNotNull();
        assertThat(createdFileUploadExercise.getId()).isNotNull();
        assertThat(createdFileUploadExercise.getFilePattern()).isEqualTo(creationFilePattern.toLowerCase().replaceAll("\\s+", ""));
        assertThat(createdFileUploadExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(createdFileUploadExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(createdFileUploadExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());

        GradingCriterion criterionWithoutTitle = GradingCriterionUtil.findGradingCriterionByTitle(createdFileUploadExercise, null);
        assertThat(criterionWithoutTitle.getStructuredGradingInstructions()).hasSize(1);
        assertThat(criterionWithoutTitle.getStructuredGradingInstructions().stream().findFirst().orElseThrow().getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);

        request.postWithResponseBody("/api/file-upload-exercises", invalidDates.applyTo(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam(creationFilePattern, exerciseGroup);
        fileUploadExercise.setCourse(fileUploadExercise.getExerciseGroup().getExam().getCourse());

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setCourse(null);
        fileUploadExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getFileUploadExercise() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");

        exerciseUtilService.addChannelToExercise(fileUploadExercise);

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);

        assertThat(fileUploadExercise.getId()).isEqualTo(receivedFileUploadExercise.getId());
        assertThat(fileUploadExercise).isEqualTo(receivedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getExamFileUploadExercise_asStudent_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExamFileUploadExercise_asTutor_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    private void getExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise();
        request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamFileUploadExercise_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise();

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);
        assertThat(receivedFileUploadExercise).as("exercise was retrieved").isNotNull();
        assertThat(receivedFileUploadExercise.getId()).as("exercise with the right id was retrieved").isEqualTo(fileUploadExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getFileUploadExerciseFails_wrongId() throws Exception {
        fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        request.get("/api/file-upload-exercises/" + 555555, HttpStatus.NOT_FOUND, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamFileUploadExercise_InstructorNotInGroup() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        for (var exercise : course.getExercises()) {
            request.get("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFileUploadExercise_setGradingInstructionFeedbackUsed() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> true).orElseThrow());
        feedbackRepository.save(feedback);

        exerciseUtilService.addChannelToExercise(fileUploadExercise);

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);

        assertThat(receivedFileUploadExercise.isGradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExercise_asInstructor() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        for (var exercise : course.getExercises()) {
            request.delete("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.OK);
        }
        assertThat(exerciseRepo.findByCourseIdWithCategories(course.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFileUploadExerciseWithChannel() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        Channel exerciseChannel = exerciseUtilService.addChannelToExercise(fileUploadExercise);

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteFileUploadExercise_asStudent() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        for (var exercise : course.getExercises()) {
            request.delete("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        }

        assertThat(exerciseRepo.findByCourseIdWithCategories(course.getId())).hasSize(course.getExercises().size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExerciseFails_WithWrongId() throws Exception {
        fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        request.delete("/api/file-upload-exercises/" + 5555555, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExerciseFails_InstructorNotInGroup() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        for (var exercise : course.getExercises()) {
            request.delete("/api/file-upload-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        }
        assertThat(exerciseRepo.findByCourseIdWithCategories(course.getId())).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise();
        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        assertThat(exerciseRepo.findByCourseIdWithCategories(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_asInstructor() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseFails_InstructorNotInGroup() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        fileUploadExercise.setDueDate(ZonedDateTime.now().plusDays(10));
        fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(11));
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise();
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_invalid_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise();

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), dates.applyTo(fileUploadExercise), FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise();
        fileUploadExercise.setCourse(fileUploadExercise.getExerciseGroup().getExam().getCourse());

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setExerciseGroup(null);
        fileUploadExercise.setCourse(null);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_conversionBetweenCourseAndExamExercise_badRequest() throws Exception {
        FileUploadExercise fileUploadExerciseWithCourse = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        FileUploadExercise fileUploadExerciseWithExerciseGroup = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise();

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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateModelingExerciseDueDate() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise = fileUploadExerciseRepository.save(fileUploadExercise);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final FileUploadSubmission submission1 = ParticipationFactory.generateFileUploadSubmission(true);
            fileUploadExerciseUtilService.addFileUploadSubmission(fileUploadExercise, submission1, TEST_PREFIX + "student1");
            final FileUploadSubmission submission2 = ParticipationFactory.generateFileUploadSubmission(true);
            fileUploadExerciseUtilService.addFileUploadSubmission(fileUploadExercise, submission2, TEST_PREFIX + "student2");

            final var participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(fileUploadExercise.getId()));
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllFileUploadExercisesForCourse_asInstructor() throws Exception {
        var course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        List<FileUploadExercise> receivedFileUploadExercises = request.getList("/api/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.OK,
                FileUploadExercise.class);

        // this seems to be a flaky test, based on the execution order, the following line has a problem with authentication, this should fix it
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        assertThat(receivedFileUploadExercises).hasSize(course.getExercises().size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllFileUploadExercisesForCourseFails_InstructorNotInGroup() throws Exception {
        var course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        course.setInstructorGroupName("new-instructor-group-name");
        courseRepo.save(course);
        request.getList("/api/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllFileUploadExercisesForCourse_asStudent() throws Exception {
        var course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        request.getList("/api/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        StudentParticipation participation = participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(fileUploadExercise, TEST_PREFIX + "instructor1");

        // change grading instruction score
        Set<GradingInstruction> usedInstructions = participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream())
                .flatMap(result -> result.getFeedbacks().stream()).flatMap(feedback -> Optional.ofNullable(feedback.getGradingInstruction()).stream())
                .collect(Collectors.toUnmodifiableSet());
        assertThat(usedInstructions).hasSize(1);
        GradingInstruction usedInstruction = usedInstructions.stream().findAny().orElseThrow();
        usedInstruction.setCredits(3);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody(
                "/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", fileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedFileUploadExercise);
        assertThat(GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> instruction.getId().equals(usedInstruction.getId())).orElseThrow().getCredits())
                .isEqualTo(3);
        assertThat(updatedResults.get(0).getScore()).isEqualTo(60);
        assertThat(updatedResults.get(0).getFeedbacks().get(0).getCredits()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_shouldDeleteFeedbacks() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(fileUploadExercise, TEST_PREFIX + "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.removeIf(criterion -> criterion.getTitle() == null);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody(
                "/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate" + "?deleteFeedback=true", fileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedFileUploadExercise);
        assertThat(updatedFileUploadExercise.getGradingCriteria()).hasSize(2);
        assertThat(updatedResults.get(0).getScore()).isZero();
        assertThat(updatedResults.get(0).getFeedbacks()).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        course.setInstructorGroupName("test");
        courseRepo.save(course);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate", fileUploadExercise, FileUploadExercise.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        FileUploadExercise fileUploadExerciseToBeConflicted = fileUploadExerciseRepository.findByIdElseThrow(fileUploadExercise.getId());
        fileUploadExerciseToBeConflicted.setId(123456789L);
        fileUploadExerciseRepository.save(fileUploadExerciseToBeConflicted);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate", fileUploadExerciseToBeConflicted, FileUploadExercise.class,
                HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_notFound() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        FileUploadExercise fileUploadExercise = exerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");

        request.putWithResponseBody("/api/file-upload-exercises/" + 123456789 + "/re-evaluate", fileUploadExercise, FileUploadExercise.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        fileUploadExercise.setId(null);
        fileUploadExercise.setAssessmentDueDate(null);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(3));
        fileUploadExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(3));
        fileUploadExercise.setDueDate(null);
        fileUploadExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        fileUploadExercise.setId(null);
        fileUploadExercise.setAssessmentDueDate(null);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        fileUploadExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);

        fileUploadExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 4));
        var result = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        fileUploadExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        fileUploadExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        result = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFileUploadExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetFileUploadExercise_exampleSolutionVisibility(true, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseFromCourseToCourseAsEditorSuccess() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        Exercise expectedFileUploadExercise = course.getExercises().stream().findFirst().orElseThrow();
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);
        expectedFileUploadExercise.setCourse(course2);
        String uniqueChannelName = "test" + UUID.randomUUID().toString().substring(0, 8);
        expectedFileUploadExercise.setChannelName(uniqueChannelName);
        var sourceExerciseId = expectedFileUploadExercise.getId();
        var importedFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises/import/" + sourceExerciseId, expectedFileUploadExercise, FileUploadExercise.class,
                HttpStatus.CREATED);
        assertThat(importedFileUploadExercise).usingRecursiveComparison()
                .ignoringFields("id", "course", "shortName", "releaseDate", "dueDate", "assessmentDueDate", "exampleSolutionPublicationDate", "channelNameTransient")
                .isEqualTo(expectedFileUploadExercise);
        Channel channelFromDB = channelRepository.findChannelByExerciseId(importedFileUploadExercise.getId());
        assertThat(channelFromDB).isNotNull();
        assertThat(channelFromDB.getName()).isEqualTo(uniqueChannelName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseFromCourseToCourseNegativeCourseIdBadRequest() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        Exercise expectedFileUploadExercise = course.getExercises().stream().findFirst().orElseThrow();
        Course course2 = courseUtilService.addEmptyCourse();
        expectedFileUploadExercise.setCourse(course2);
        request.postWithResponseBody("/api/file-upload-exercises/import/" + -1, expectedFileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseCourseNotSetBadRequest() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        Exercise expectedFileUploadExercise = course.getExercises().stream().findFirst().orElseThrow();
        expectedFileUploadExercise.setCourse(null);
        request.postWithResponseBody("/api/file-upload-exercises/import/" + expectedFileUploadExercise.getId(), expectedFileUploadExercise, FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAllExercisesOnPageAsEditorSuccess() throws Exception {
        final Course course = courseUtilService.addEmptyCourse();
        final var now = ZonedDateTime.now();
        FileUploadExercise exercise = FileUploadExerciseFactory.generateFileUploadExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), "pdf", course);
        String title = TEST_PREFIX + "testGetAllExercisesOnPageAsEditorSuccess";
        exercise.setTitle(title);
        exercise = fileUploadExerciseRepository.save(exercise);
        final var searchTerm = pageableSearchUtilService.configureSearch(exercise.getTitle());
        SearchResultPageDTO<Exercise> result = request.getSearchResult("/api/file-upload-exercises", HttpStatus.OK, Exercise.class,
                pageableSearchUtilService.searchMapping(searchTerm));
        assertThat(result.getResultsOnPage()).hasSize(1);
        assertThat(result.getNumberOfPages()).isEqualTo(1);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "ta1", roles = "TA")
    void testImportFileUploadExerciseAsTeachingAssistantFails() throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        Exercise expectedFileUploadExercise = course.getExercises().stream().findFirst().orElseThrow();
        var sourceExerciseId = expectedFileUploadExercise.getId();
        request.postWithResponseBody("/api/file-upload-exercises/import/" + sourceExerciseId, expectedFileUploadExercise, FileUploadExercise.class, HttpStatus.FORBIDDEN);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testExamExerciseNotIncludedInScoreReturnsBadRequest() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise();
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/file-upload-exercises/import/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFileUploadExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetFileUploadExercise_exampleSolutionVisibility(false, TEST_PREFIX + "instructor1");
    }

    private void testGetFileUploadExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        Course course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        final FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        // Utility function to avoid duplication
        Function<Course, FileUploadExercise> fileUploadExerciseGetter = c -> (FileUploadExercise) c.getExercises().stream()
                .filter(e -> e.getId().equals(fileUploadExercise.getId())).findAny().orElseThrow();

        fileUploadExercise.setExampleSolution("Sample<br>solution");

        if (isStudent) {
            participationUtilService.createAndSaveParticipationForExercise(fileUploadExercise, username);
        }

        // Test example solution publication date not set.
        fileUploadExercise.setExampleSolutionPublicationDate(null);
        fileUploadExerciseRepository.save(fileUploadExercise);

        CourseForDashboardDTO courseForDashboard = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard",
                HttpStatus.OK, CourseForDashboardDTO.class);
        course = courseForDashboard.course();
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

        courseForDashboard = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());

        // Test example solution publication date in the future.
        fileUploadExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        fileUploadExerciseRepository.save(fileUploadExercise);

        courseForDashboard = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());
        }
    }
}
