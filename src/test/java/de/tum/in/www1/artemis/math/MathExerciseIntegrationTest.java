package de.tum.in.www1.artemis.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;

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
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.mathexercise.MathExerciseFactory;
import de.tum.in.www1.artemis.exercise.mathexercise.MathExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.ExerciseIntegrationTestUtils;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CourseForDashboardDTO;

class MathExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mathexerciseintegration";

    @Autowired
    private MathExerciseRepository mathExerciseRepository;

    @Autowired
    private MathExerciseUtilService mathExerciseUtilService;

    @Autowired
    private MathSubmissionRepository mathSubmissionRepository;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ExerciseIntegrationTestUtils exerciseIntegrationTestUtils;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        userUtilService.addInstructor("other-instructors", TEST_PREFIX + "instructorother");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void submitMathExercise() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathSubmission mathSubmission = ParticipationFactory.generateMathSubmission("test-submission", false);
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        request.postWithResponseBody("/api/exercises/" + mathExercise.getId() + "/participations", null, Participation.class);
        mathSubmission = request.postWithResponseBody("/api/exercises/" + mathExercise.getId() + "/math-submissions", mathSubmission, MathSubmission.class);

        Optional<MathSubmission> result = mathSubmissionRepository.findById(mathSubmission.getId());
        assertThat(result).isPresent();
        result.ifPresent(submission -> assertThat(submission.getText()).isEqualTo("test-submission"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteMathExerciseWithSubmissionWithTextBlocks() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        MathSubmission mathSubmission = ParticipationFactory.generateMathSubmission("test-submission", true);
        mathSubmission = mathExerciseUtilService.saveMathSubmission(mathExercise, mathSubmission, TEST_PREFIX + "student1");

        request.delete("/api/math-exercises/" + mathExercise.getId(), HttpStatus.OK);
        assertThat(mathExerciseRepository.findById(mathExercise.getId())).as("math exercise was deleted").isEmpty();
        assertThat(mathSubmissionRepository.findById(mathSubmission.getId())).as("math submission was deleted").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteMathExerciseWithChannel() throws Exception {
        Course course = courseUtilService.createCourse();
        ZonedDateTime now = ZonedDateTime.now();
        MathExercise mathExercise = mathExerciseUtilService.createIndividualMathExercise(course, now, now, now);
        Channel exerciseChannel = exerciseUtilService.addChannelToExercise(mathExercise);

        request.delete("/api/math-exercises/" + mathExercise.getId(), HttpStatus.OK);

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExamMathExercise() throws Exception {
        MathExercise mathExercise = mathExerciseUtilService.addCourseExamExerciseGroupWithOneMathExercise();

        request.delete("/api/math-exercises/" + mathExercise.getId(), HttpStatus.OK);
        assertThat(mathExerciseRepository.findById(mathExercise.getId())).isNotPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteMathExercise_notFound() throws Exception {
        MathExercise mathExercise = new MathExercise();
        mathExercise.setId(114213211L);

        request.delete("/api/math-exercises/" + mathExercise.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteMathExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.delete("/api/math-exercises/" + mathExercise.getId(), HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "exercise-new-math-exercise", "" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise(String channelName) throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        courseUtilService.enableMessagingForCourse(course);
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        String title = "New Math Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;

        mathExercise.setId(null);
        mathExercise.setTitle(title);
        mathExercise.setDifficulty(difficulty);
        mathExercise.setChannelName(channelName);
        MathExercise newMathExercise = request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.CREATED);

        Channel channel = channelRepository.findChannelByExerciseId(newMathExercise.getId());

        assertThat(newMathExercise.getTitle()).as("math exercise title was correctly set").isEqualTo(title);
        assertThat(newMathExercise.getDifficulty()).as("math exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newMathExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(newMathExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(newMathExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("exerciseGroupId was set correctly").isEqualTo(course.getId());
        assertThat(channel).as("channel was created").isNotNull();
        assertThat(channel.getName()).as("channel name was set correctly").isEqualTo("exercise-new-math-exercise");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_setExerciseTitleNull_badRequest() throws Exception {
        MathExercise mathExercise = new MathExercise();

        request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_setAssessmentDueDateWithoutExerciseDueDate_badRequest() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setId(null);
        mathExercise.setDueDate(null);

        request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        mathExercise.setId(null);

        request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);

        String title = "New Exam Math Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        mathExercise.setTitle(title);
        mathExercise.setDifficulty(difficulty);
        mathExercise.setChannelName("new-exam-math-exercise");
        MathExercise newMathExercise = request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.CREATED);
        Channel channel = channelRepository.findChannelByExerciseId(newMathExercise.getId());
        assertThat(channel).isNull(); // there should not be any channel for exam exercise

        assertThat(newMathExercise.getTitle()).as("math exercise title was correctly set").isEqualTo(title);
        assertThat(newMathExercise.getDifficulty()).as("math exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newMathExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(newMathExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(newMathExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExerciseForExam_datesSet() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        ZonedDateTime someMoment = ZonedDateTime.of(2000, 6, 15, 0, 0, 0, 0, ZoneId.of("Z"));
        String title = "New Exam Math Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        mathExercise.setTitle(title);
        mathExercise.setDifficulty(difficulty);
        mathExercise.setDueDate(someMoment);
        request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
        assertThat(exerciseGroup.getExercises()).doesNotContain(mathExercise);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        request.postWithResponseBody("/api/math-exercises/", invalidDates.applyTo(mathExercise), MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        mathExercise.setCourse(exerciseGroup.getExam().getCourse());
        request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(null);

        request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_InvalidMaxScore() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setMaxPoints(0.0);
        request.putWithResponseBody("/api/math-exercises", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setMaxPoints(10.0);
        mathExercise.setBonusPoints(1.0);
        mathExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.putWithResponseBody("/api/math-exercises", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_NotIncludedInvalidBonusPoints() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setMaxPoints(10.0);
        mathExercise.setBonusPoints(1.0);
        mathExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.putWithResponseBody("/api/math-exercises", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_WithStructuredGradingInstructions() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        GradingCriterion criterion = new GradingCriterion();
        criterion.setTitle("Test");

        GradingInstruction gradingInstruction = new GradingInstruction();
        gradingInstruction.setCredits(2);
        gradingInstruction.setGradingScale("Good");
        gradingInstruction.setInstructionDescription("Use this Feedback to test functionality");
        gradingInstruction.setFeedback("This is a test!");
        gradingInstruction.setUsageCount(5);

        criterion.addStructuredGradingInstruction(gradingInstruction);
        mathExercise.setGradingCriteria(List.of(criterion));
        MathExercise actualExercise = request.putWithResponseBody("/api/math-exercises", mathExercise, MathExercise.class, HttpStatus.OK);

        assertThat(actualExercise.getGradingCriteria()).hasSize(1);
        assertThat(actualExercise.getGradingCriteria().get(0).getTitle()).isEqualTo("Test");
        assertThat(actualExercise.getGradingCriteria().get(0).getStructuredGradingInstructions())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "gradingCriterion").containsExactly(gradingInstruction);
        assertThat(actualExercise.getGradingCriteria().get(0).getExercise().getId()).isEqualTo(actualExercise.getId());
        assertThat(actualExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0).getGradingCriterion().getId())
                .isEqualTo(actualExercise.getGradingCriteria().get(0).getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise = mathExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(mathExercise.getId());

        // update certain attributes of math exercise
        String title = "Updated Math Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        mathExercise.setTitle(title);
        mathExercise.setDifficulty(difficulty);

        // add example submission to exercise
        MathSubmission mathSubmission = ParticipationFactory.generateMathSubmission("Lorem Ipsum Foo Bar", true);
        mathSubmissionRepository.save(mathSubmission);
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setSubmission(mathSubmission);
        exampleSubmission.setExercise(mathExercise);
        exampleSubmissionRepo.save(exampleSubmission);
        mathExercise.addExampleSubmission(exampleSubmission);

        MathExercise updatedMathExercise = request.putWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.OK);

        assertThat(updatedMathExercise.getTitle()).as("math exercise title was correctly updated").isEqualTo(title);
        assertThat(updatedMathExercise.getDifficulty()).as("math exercise difficulty was correctly updated").isEqualTo(difficulty);
        assertThat(updatedMathExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(updatedMathExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(updatedMathExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("courseId was not updated").isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExerciseDueDate() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        final MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final MathSubmission submission1 = ParticipationFactory.generateMathSubmission("Lorem Ipsum Foo Bar", true);
            mathExerciseUtilService.saveMathSubmission(mathExercise, submission1, TEST_PREFIX + "student1");
            final MathSubmission submission2 = ParticipationFactory.generateMathSubmission("Lorem Ipsum Foo Bar", true);
            mathExerciseUtilService.saveMathSubmission(mathExercise, submission2, TEST_PREFIX + "student2");

            final var participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(mathExercise.getId()));
            assertThat(participations).hasSize(2);
            participations.get(0).setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            studentParticipationRepository.saveAll(participations);
        }

        mathExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        request.put("/api/math-exercises/", mathExercise, HttpStatus.OK);

        {
            final var participations = studentParticipationRepository.findByExerciseId(mathExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(individualDueDate);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_setExerciseIdNull_created() throws Exception {
        Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setId(null);
        mathExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        request.putWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_updatingCourseId_asInstructor() throws Exception {
        // Create a math exercise.
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise existingMathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        // Create a new course with different id.
        Long oldCourseId = course.getId();
        Long newCourseId = oldCourseId + 1L;
        Course newCourse = courseUtilService.createCourse(newCourseId);

        // Assign new course to the math exercise.
        existingMathExercise.setCourse(newCourse);

        // Math exercise update with the new course should fail.
        MathExercise returnedMathExercise = request.putWithResponseBody("/api/math-exercises", existingMathExercise, MathExercise.class, HttpStatus.CONFLICT);
        assertThat(returnedMathExercise).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.putWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        mathExerciseRepository.save(mathExercise);

        // Update certain attributes of math exercise
        String updateTitle = "After";
        DifficultyLevel updateDifficulty = DifficultyLevel.HARD;
        mathExercise.setTitle(updateTitle);
        mathExercise.setDifficulty(updateDifficulty);

        MathExercise updatedMathExercise = request.putWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.OK);

        assertThat(updatedMathExercise.getTitle()).as("math exercise title was correctly updated").isEqualTo(updateTitle);
        assertThat(updatedMathExercise.getDifficulty()).as("math exercise difficulty was correctly updated").isEqualTo(updateDifficulty);
        assertThat(updatedMathExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(updatedMathExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedMathExercise.getExerciseGroup().getId()).as("exerciseGroupId was not updated").isEqualTo(exerciseGroup.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        mathExerciseRepository.save(mathExercise);

        request.putWithResponseBody("/api/math-exercises/", invalidDates.applyTo(mathExercise), MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setCourse(null);

        request.putWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_convertFromCourseToExamExercise_badRequest() throws Exception {
        Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);

        mathExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_convertFromExamToCourseExercise_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        mathExerciseRepository.save(mathExercise);

        mathExercise.setExerciseGroup(null);

        request.putWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExerciseFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);
        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        mathExerciseRepository.save(mathExercise);
        mathExercise.setCourse(course2);
        mathExercise.setChannelName("testchannel" + mathExercise.getId());
        var newMathExercise = request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.CREATED);
        Channel channel = channelRepository.findChannelByExerciseId(newMathExercise.getId());
        assertThat(channel).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExerciseWithExampleSubmissionFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        mathExercise = mathExerciseRepository.save(mathExercise);
        mathExercise.setChannelName("testchannel" + mathExercise.getId());
        // Create example submission
        var exampleSubmission = participationUtilService.generateExampleSubmission("Lorem Ipsum", mathExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);

        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        MathExercise newMathExercise = request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.CREATED);
        assertThat(newMathExercise.getExampleSubmissions()).hasSize(1);
        ExampleSubmission newExampleSubmission = newMathExercise.getExampleSubmissions().iterator().next();
        MathSubmission newSubmission = (MathSubmission) newExampleSubmission.getSubmission();
        assertThat(newSubmission.getText()).isEqualTo(((MathSubmission) exampleSubmission.getSubmission()).getText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExerciseFromCourseToExam() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        mathExerciseRepository.save(mathExercise);
        mathExercise.setCourse(null);
        mathExercise.setDueDate(null);
        mathExercise.setAssessmentDueDate(null);
        mathExercise.setReleaseDate(null);
        mathExercise.setExerciseGroup(exerciseGroup1);

        var newMathExercise = request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.CREATED);

        // There should not be created a channel for the imported exam exercise
        Channel channel = channelRepository.findChannelByExerciseId(newMathExercise.getId());
        assertThat(channel).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void importMathExerciseFromCourseToExam_forbidden() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        mathExerciseRepository.save(mathExercise);
        mathExercise.setCourse(null);
        mathExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExerciseFromExamToCourse() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup1);
        Course course1 = courseUtilService.addEmptyCourse();
        mathExerciseRepository.save(mathExercise);
        mathExercise.setCourse(course1);
        mathExercise.setExerciseGroup(null);
        mathExercise.setChannelName("test" + mathExercise.getId());
        request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void importMathExerciseFromExamToCourse_forbidden() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup1);
        Course course1 = courseUtilService.addEmptyCourse();
        mathExerciseRepository.save(mathExercise);
        mathExercise.setCourse(course1);
        mathExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ExerciseGroup exerciseGroup2 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup1);
        mathExerciseRepository.save(mathExercise);
        mathExercise.setExerciseGroup(exerciseGroup2);

        request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExerciseFromCourseToCourse_badRequest() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        mathExerciseRepository.save(mathExercise);
        mathExercise.setCourse(null);

        request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);

        mathExercise.setExampleSolutionPublicationDate(ZonedDateTime.now());

        mathExercise = mathExerciseRepository.save(mathExercise);
        mathExercise.setCourse(course2);
        mathExercise.setChannelName("test-" + mathExercise.getId());
        MathExercise newMathExercise = request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.CREATED);
        assertThat(newMathExercise.getExampleSolutionPublicationDate()).as("text example solution publication date was correctly set to null in the response").isNull();

        MathExercise newMathExerciseFromDatabase = mathExerciseRepository.findById(newMathExercise.getId()).orElseThrow();
        assertThat(newMathExerciseFromDatabase.getExampleSolutionPublicationDate()).as("text example solution publication date was correctly set to null in the database").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllMathExercisesForCourse() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();

        List<MathExercise> mathExercises = request.getList("/api/courses/" + course.getId() + "/math-exercises/", HttpStatus.OK, MathExercise.class);

        assertThat(mathExercises).as("math exercises for course were retrieved").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllMathExercisesForCourse_isNotAtLeastTeachingAssistantInCourse_forbidden() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        request.getList("/api/courses/" + course.getId() + "/math-exercises/", HttpStatus.FORBIDDEN, MathExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathExercise_notFound() throws Exception {
        MathExercise mathExercise = new MathExercise();
        mathExercise.setId(114213211L);

        request.get("/api/math-exercises/" + mathExercise.getId(), HttpStatus.NOT_FOUND, MathExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathExerciseAsTutor() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        Channel channel = new Channel();
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        channel.setExercise(mathExercise);
        channelRepository.save(channel);
        MathExercise mathExerciseServer = request.get("/api/math-exercises/" + mathExercise.getId(), HttpStatus.OK, MathExercise.class);

        assertThat(mathExerciseServer).as("math exercise was retrieved").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExamMathExerciseAsTutor_forbidden() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        mathExerciseRepository.save(mathExercise);

        request.get("/api/math-exercises/" + mathExercise.getId(), HttpStatus.FORBIDDEN, MathExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamMathExerciseAsInstructor() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        MathExercise mathExercise = MathExerciseFactory.generateMathExerciseForExam(exerciseGroup);
        mathExerciseRepository.save(mathExercise);

        MathExercise mathExerciseServer = request.get("/api/math-exercises/" + mathExercise.getId(), HttpStatus.OK, MathExercise.class);
        assertThat(mathExerciseServer).as("math exercise was retrieved").isNotNull();
        assertThat(mathExerciseServer.getId()).as("Math exercise with the right id was retrieved").isEqualTo(mathExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathExercise_isNotAtleastTeachingAssistantInCourse_forbidden() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);
        request.get("/api/math-exercises/" + mathExercise.getId(), HttpStatus.FORBIDDEN, MathExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetMathExercise_setGradingInstructionFeedbackUsed() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        Channel channel = new Channel();
        channel.setName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setExercise(mathExercise);
        channelRepository.save(channel);
        List<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(mathExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(gradingCriteria.get(0).getStructuredGradingInstructions().get(0));
        feedbackRepository.save(feedback);

        MathExercise receivedMathExercise = request.get("/api/math-exercises/" + mathExercise.getId(), HttpStatus.OK, MathExercise.class);

        assertThat(receivedMathExercise.isGradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetResultsFromOwningCoursesNotEmpty() throws Exception {
        String courseBaseTitle1 = "testInstructorGetResultsFromOwningCoursesNotEmpty 1";
        String courseBaseTitle2 = "testInstructorGetResultsFromOwningCoursesNotEmpty 2";

        mathExerciseUtilService.addCourseWithOneReleasedMathExercise(courseBaseTitle1);
        mathExerciseUtilService.addCourseWithOneReleasedMathExercise(courseBaseTitle2 + "Bachelor");
        mathExerciseUtilService.addCourseWithOneReleasedMathExercise(courseBaseTitle2 + "Master");

        final var searchText = pageableSearchUtilService.configureSearch(courseBaseTitle1);
        final var resultText = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(searchText));
        assertThat(resultText.getResultsOnPage()).hasSize(1);

        final var searchEssay = pageableSearchUtilService.configureSearch(courseBaseTitle2);
        final var resultEssay = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage()).hasSize(2);

        final var searchNon = pageableSearchUtilService.configureSearch("No course has this name");
        final var resultNon = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(searchNon));
        assertThat(resultNon.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseAndExamFiltersAsInstructor() throws Exception {
        testCourseAndExamFilters("testCourseAndExamFiltersAsInstructor");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCourseAndExamFiltersAsAdmin() throws Exception {
        testCourseAndExamFilters("testCourseAndExamFiltersAsAdmin");
    }

    private void testCourseAndExamFilters(String courseTitle) throws Exception {
        mathExerciseUtilService.addCourseWithOneReleasedMathExercise(courseTitle);
        mathExerciseUtilService.addCourseExamExerciseGroupWithOneMathExercise(courseTitle + "-Morpork");
        exerciseIntegrationTestUtils.testCourseAndExamFilters("/api/math-exercises", courseTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorSearchTermMatchesId() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminSearchTermMatchesId() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    private void testSearchTermMatchesId() throws Exception {
        final Course course = courseUtilService.addEmptyCourse();
        final var now = ZonedDateTime.now();
        MathExercise exercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course);
        exercise.setTitle("LoremIpsum");
        exercise = mathExerciseRepository.save(exercise);
        var exerciseId = exercise.getId();

        final var searchTerm = pageableSearchUtilService.configureSearch(exerciseId.toString());
        final var searchResult = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage().stream().filter(result -> result.getId() == exerciseId.intValue())).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningExams() throws Exception {
        mathExerciseUtilService.addCourseExamExerciseGroupWithOneMathExercise();
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetResultsFromOwningExamsNotEmpty() throws Exception {
        String exerciseBaseTitle1 = "testInstructorGetResultsFromOwningExamsNotEmpty 1";
        String exerciseBaseTitle2 = "testInstructorGetResultsFromOwningExamsNotEmpty 2";

        mathExerciseUtilService.addCourseExamExerciseGroupWithOneMathExercise(exerciseBaseTitle1);
        mathExerciseUtilService.addCourseExamExerciseGroupWithOneMathExercise(exerciseBaseTitle2 + "Bachelor");
        mathExerciseUtilService.addCourseExamExerciseGroupWithOneMathExercise(exerciseBaseTitle2 + "Master");

        final var searchText = pageableSearchUtilService.configureSearch(exerciseBaseTitle1);
        final var resultText = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(searchText));
        assertThat(resultText.getResultsOnPage()).hasSize(1);

        final var searchEssay = pageableSearchUtilService.configureSearch(exerciseBaseTitle2);
        final var resultEssay = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage()).hasSize(2);

        final var searchNon = pageableSearchUtilService.configureSearch("No exam has this name");
        final var resultNon = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(searchNon));
        assertThat(resultNon.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        String courseTitle = "testAdminGetsResultsFromAllCourses";

        mathExerciseUtilService.addCourseWithOneReleasedMathExercise(courseTitle);
        Course otherInstructorsCourse = mathExerciseUtilService.addCourseWithOneReleasedMathExercise(courseTitle);
        otherInstructorsCourse.setInstructorGroupName("other-instructors");
        courseRepository.save(otherInstructorsCourse);

        final var search = pageableSearchUtilService.configureSearch(courseTitle);
        final var result = request.getSearchResult("/api/math-exercises", HttpStatus.OK, MathExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportMathExercise_team_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        MathExercise sourceExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        sourceExercise = mathExerciseRepository.save(sourceExercise);

        var exerciseToBeImported = new MathExercise();
        exerciseToBeImported.setMode(ExerciseMode.TEAM);

        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exerciseToBeImported);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exerciseToBeImported.setTeamAssignmentConfig(teamAssignmentConfig);
        exerciseToBeImported.setCourse(course2);
        exerciseToBeImported.setMaxPoints(1.0);
        exerciseToBeImported.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 3));

        exerciseToBeImported = request.postWithResponseBody("/api/math-exercises/import/" + sourceExercise.getId(), exerciseToBeImported, MathExercise.class, HttpStatus.CREATED);

        assertThat(exerciseToBeImported.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course2.getId());
        assertThat(exerciseToBeImported.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(exerciseToBeImported.getTeamAssignmentConfig().getMinTeamSize()).isEqualTo(teamAssignmentConfig.getMinTeamSize());
        assertThat(exerciseToBeImported.getTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(teamAssignmentConfig.getMaxTeamSize());
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null)).isEmpty();

        sourceExercise = mathExerciseRepository.findById(sourceExercise.getId()).orElseThrow();
        assertThat(sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course1.getId());
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(sourceExercise.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportMathExercise_individual_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        MathExercise sourceExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        sourceExercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(sourceExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        sourceExercise.setCourse(course1);

        sourceExercise = mathExerciseRepository.save(sourceExercise);
        var team = new Team();
        team.setShortName("testImportMathExercise_individual_modeChange");
        teamRepository.save(sourceExercise, team);

        var exerciseToBeImported = new MathExercise();
        exerciseToBeImported.setMode(ExerciseMode.INDIVIDUAL);
        exerciseToBeImported.setCourse(course2);
        exerciseToBeImported.setMaxPoints(1.0);
        exerciseToBeImported.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 3));

        exerciseToBeImported = request.postWithResponseBody("/api/math-exercises/import/" + sourceExercise.getId(), exerciseToBeImported, MathExercise.class, HttpStatus.CREATED);

        assertThat(exerciseToBeImported.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course2.getId());
        assertThat(exerciseToBeImported.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(exerciseToBeImported.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null)).isEmpty();

        sourceExercise = mathExerciseRepository.findById(sourceExercise.getId()).orElseThrow();
        assertThat(sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course1.getId());
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateMathExercise() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        List<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(mathExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(mathExercise, TEST_PREFIX + "instructor1");

        // change grading instruction score
        gradingCriteria.get(0).getStructuredGradingInstructions().get(0).setCredits(3);
        gradingCriteria.remove(1);
        mathExercise.setGradingCriteria(gradingCriteria);

        MathExercise updatedMathExercise = request.putWithResponseBody("/api/math-exercises/" + mathExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", mathExercise,
                MathExercise.class, HttpStatus.OK);
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedMathExercise);
        assertThat(updatedMathExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0).getCredits()).isEqualTo(3);
        assertThat(updatedResults.get(0).getScore()).isEqualTo(60);
        assertThat(updatedResults.get(0).getFeedbacks().get(0).getCredits()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateMathExerciseWithExampleSubmission() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        List<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(mathExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        gradingCriteria.remove(1);
        mathExercise.setGradingCriteria(gradingCriteria);

        // Create example submission
        Set<ExampleSubmission> exampleSubmissionSet = new HashSet<>();
        var exampleSubmission = participationUtilService.generateExampleSubmission("text", mathExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        MathSubmission mathSubmission = (MathSubmission) participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        mathSubmission.setExampleSubmission(true);
        Result result = mathSubmission.getLatestResult();
        result.setExampleResult(true);
        mathSubmission.addResult(result);
        mathSubmissionRepository.save(mathSubmission);
        exampleSubmissionSet.add(exampleSubmission);
        mathExercise.setExampleSubmissions(exampleSubmissionSet);

        request.putWithResponseBody("/api/math-exercises/" + mathExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", mathExercise, MathExercise.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateMathExercise_shouldDeleteFeedbacks() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        List<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(mathExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(mathExercise, TEST_PREFIX + "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.remove(1);
        gradingCriteria.remove(0);
        mathExercise.setGradingCriteria(gradingCriteria);

        MathExercise updatedMathExercise = request.putWithResponseBody("/api/math-exercises/" + mathExercise.getId() + "/re-evaluate" + "?deleteFeedback=true", mathExercise,
                MathExercise.class, HttpStatus.OK);
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedMathExercise);
        assertThat(updatedMathExercise.getGradingCriteria()).hasSize(1);
        assertThat(updatedResults.get(0).getScore()).isZero();
        assertThat(updatedResults.get(0).getFeedbacks()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateMathExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.putWithResponseBody("/api/math-exercises/" + mathExercise.getId() + "/re-evaluate", mathExercise, MathExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateMathExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        MathExercise mathExerciseToBeConflicted = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExerciseToBeConflicted.setId(123456789L);
        mathExerciseRepository.save(mathExerciseToBeConflicted);

        request.putWithResponseBody("/api/math-exercises/" + mathExercise.getId() + "/re-evaluate", mathExerciseToBeConflicted, MathExercise.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateMathExercise_notFound() throws Exception {
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        request.putWithResponseBody("/api/math-exercises/" + 123456789 + "/re-evaluate", mathExercise, MathExercise.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setId(null);
        mathExercise.setAssessmentDueDate(null);
        mathExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        mathExercise.setReleaseDate(baseTime.plusHours(1));
        mathExercise.setDueDate(baseTime.plusHours(3));
        mathExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);

        mathExercise.setReleaseDate(baseTime.plusHours(3));
        mathExercise.setDueDate(null);
        mathExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        mathExercise.setId(null);
        mathExercise.setAssessmentDueDate(null);
        mathExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        mathExercise.setReleaseDate(baseTime.plusHours(1));
        mathExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        mathExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        mathExercise.setChannelName("test");

        var result = request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

        mathExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        mathExercise.setReleaseDate(baseTime.plusHours(1));
        mathExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        mathExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        mathExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        result = request.postWithResponseBody("/api/math-exercises/", mathExercise, MathExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetMathExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetMathExercise_exampleSolutionVisibility(true, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetMathExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetMathExercise_exampleSolutionVisibility(false, "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportMathExercise_setGradingInstructionForCopiedFeedback() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();

        MathExercise mathExercise = MathExerciseFactory.generateMathExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        mathExercise = mathExerciseRepository.save(mathExercise);
        List<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(mathExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        GradingInstruction gradingInstruction = gradingCriteria.get(0).getStructuredGradingInstructions().get(0);
        assertThat(gradingInstruction.getFeedback()).as("Test feedback should have student readable feedback").isNotEmpty();

        // Create example submission
        var exampleSubmission = participationUtilService.generateExampleSubmission("text", mathExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        var submission = mathSubmissionRepository.findByIdWithEagerResultsAndFeedbackElseThrow(exampleSubmission.getSubmission().getId());

        Feedback feedback = ParticipationFactory.generateFeedback().get(0);
        feedback.setGradingInstruction(gradingInstruction);
        participationUtilService.addFeedbackToResult(feedback, Objects.requireNonNull(submission.getLatestResult()));

        mathExercise.setCourse(course2);
        mathExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        var importedMathExercise = request.postWithResponseBody("/api/math-exercises/import/" + mathExercise.getId(), mathExercise, MathExercise.class, HttpStatus.CREATED);

        assertThat(mathExerciseRepository.findById(importedMathExercise.getId())).isPresent();

        var importedExampleSubmission = importedMathExercise.getExampleSubmissions().stream().findFirst().orElseThrow();
        GradingInstruction importedFeedbackGradingInstruction = importedExampleSubmission.getSubmission().getLatestResult().getFeedbacks().get(0).getGradingInstruction();
        assertThat(importedFeedbackGradingInstruction).isNotNull();

        // Copy and original should have the same data but not the same ids.
        assertThat(importedFeedbackGradingInstruction.getId()).isNotEqualTo(gradingInstruction.getId());
        assertThat(importedFeedbackGradingInstruction.getGradingCriterion()).isNull(); // To avoid infinite recursion when serializing to JSON.
        assertThat(importedFeedbackGradingInstruction.getFeedback()).isEqualTo(gradingInstruction.getFeedback());
        assertThat(importedFeedbackGradingInstruction.getGradingScale()).isEqualTo(gradingInstruction.getGradingScale());
        assertThat(importedFeedbackGradingInstruction.getInstructionDescription()).isEqualTo(gradingInstruction.getInstructionDescription());
        assertThat(importedFeedbackGradingInstruction.getCredits()).isEqualTo(gradingInstruction.getCredits());
        assertThat(importedFeedbackGradingInstruction.getUsageCount()).isEqualTo(gradingInstruction.getUsageCount());

        var importedMathExerciseFromDB = mathExerciseRepository.findByIdWithExampleSubmissionsAndResults(importedMathExercise.getId()).orElseThrow();
        var importedFeedbackGradingInstructionFromDb = importedMathExerciseFromDB.getExampleSubmissions().stream().findFirst().orElseThrow().getSubmission().getLatestResult()
                .getFeedbacks().get(0).getGradingInstruction();

        assertThat(importedFeedbackGradingInstructionFromDb.getGradingCriterion().getId()).isNotEqualTo(gradingInstruction.getGradingCriterion().getId());

    }

    private void testGetMathExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        Course course = mathExerciseUtilService.addCourseWithOneReleasedMathExercise();
        final MathExercise mathExercise = mathExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        // Utility function to avoid duplication
        Function<Course, MathExercise> mathExerciseGetter = c -> (MathExercise) c.getExercises().stream().filter(e -> e.getId().equals(mathExercise.getId())).findAny()
                .orElseThrow();

        mathExercise.setExampleSolution("Sample<br>solution");

        if (isStudent) {
            participationUtilService.createAndSaveParticipationForExercise(mathExercise, username);
        }

        // Test example solution publication date not set.
        mathExercise.setExampleSolutionPublicationDate(null);
        mathExerciseRepository.save(mathExercise);

        CourseForDashboardDTO courseForDashboard = request.get("/api/courses/" + mathExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        MathExercise mathExerciseFromApi = mathExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(mathExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(mathExerciseFromApi.getExampleSolution()).isEqualTo(mathExercise.getExampleSolution());
        }

        // Test example solution publication date in the past.
        mathExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        mathExerciseRepository.save(mathExercise);

        courseForDashboard = request.get("/api/courses/" + mathExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        mathExerciseFromApi = mathExerciseGetter.apply(course);

        assertThat(mathExerciseFromApi.getExampleSolution()).isEqualTo(mathExercise.getExampleSolution());

        // Test example solution publication date in the future.
        mathExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        mathExerciseRepository.save(mathExercise);

        courseForDashboard = request.get("/api/courses/" + mathExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        mathExerciseFromApi = mathExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(mathExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(mathExerciseFromApi.getExampleSolution()).isEqualTo(mathExercise.getExampleSolution());
        }
    }
}
