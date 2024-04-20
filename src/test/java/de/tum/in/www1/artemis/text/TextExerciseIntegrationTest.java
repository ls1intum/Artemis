package de.tum.in.www1.artemis.text;

import static de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;

import org.assertj.core.data.Offset;
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
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.GradingCriterionUtil;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.plagiarism.PlagiarismUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.ExerciseIntegrationTestService;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CourseForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.plagiarism.PlagiarismComparisonStatusDTO;
import de.tum.in.www1.artemis.web.rest.dto.plagiarism.PlagiarismResultDTO;

class TextExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textexerciseintegration";

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ExerciseIntegrationTestService exerciseIntegrationTestService;

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

    @Autowired
    private PlagiarismUtilService plagiarismUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        userUtilService.addInstructor("other-instructors", TEST_PREFIX + "instructorother");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void submitEnglishTextExercise() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This Submission is written in English", Language.ENGLISH, false);
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", null, Participation.class);
        textSubmission = request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class);

        Optional<TextSubmission> result = textSubmissionRepository.findById(textSubmission.getId());
        assertThat(result).isPresent();
        result.ifPresent(submission -> assertThat(submission.getLanguage()).isEqualTo(Language.ENGLISH));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextExerciseWithSubmissionWithTextBlocks() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        int submissionCount = 5;
        int submissionSize = 4;
        var textBlocks = TextExerciseFactory.generateTextBlocks(submissionCount * submissionSize);
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(textBlocks, textSubmission);

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findById(textExercise.getId())).as("text exercise was deleted").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTextExerciseWithChannel() throws Exception {
        Course course = courseUtilService.createCourse();
        ZonedDateTime now = ZonedDateTime.now();
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, now, now, now);
        Channel exerciseChannel = exerciseUtilService.addChannelToExercise(textExercise);

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExamTextExercise() throws Exception {
        TextExercise textExercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findById(textExercise.getId())).isNotPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextExercise_notFound() throws Exception {
        TextExercise textExercise = new TextExercise();
        textExercise.setId(114213211L);

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "exercise-new-text-exercise", "" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise(String channelName) throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        courseUtilService.enableMessagingForCourse(course);
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        String title = "New Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;

        textExercise.setId(null);
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);
        textExercise.setChannelName(channelName);
        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);

        Channel channel = channelRepository.findChannelByExerciseId(newTextExercise.getId());

        assertThat(newTextExercise.getTitle()).as("text exercise title was correctly set").isEqualTo(title);
        assertThat(newTextExercise.getDifficulty()).as("text exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newTextExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(newTextExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(newTextExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("exerciseGroupId was set correctly").isEqualTo(course.getId());
        assertThat(channel).as("channel was created").isNotNull();
        assertThat(channel.getName()).as("channel name was set correctly").isEqualTo("exercise-new-text-exercise");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setExerciseTitleNull_badRequest() throws Exception {
        TextExercise textExercise = new TextExercise();

        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setAssessmentDueDateWithoutExerciseDueDate_badRequest() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setId(null);
        textExercise.setDueDate(null);

        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        textExercise.setId(null);

        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);

        String title = "New Exam Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);
        textExercise.setChannelName("new-exam-text-exercise");
        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
        Channel channel = channelRepository.findChannelByExerciseId(newTextExercise.getId());
        assertThat(channel).isNull(); // there should not be any channel for exam exercise

        assertThat(newTextExercise.getTitle()).as("text exercise title was correctly set").isEqualTo(title);
        assertThat(newTextExercise.getDifficulty()).as("text exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newTextExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(newTextExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(newTextExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam_datesSet() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        ZonedDateTime someMoment = ZonedDateTime.of(2000, 6, 15, 0, 0, 0, 0, ZoneId.of("Z"));
        String title = "New Exam Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);
        textExercise.setDueDate(someMoment);
        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
        assertThat(exerciseGroup.getExercises()).doesNotContain(textExercise);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        request.postWithResponseBody("/api/text-exercises", invalidDates.applyTo(textExercise), TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExercise.setCourse(exerciseGroup.getExam().getCourse());
        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(null);

        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_InvalidMaxScore() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setMaxPoints(0.0);
        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(1.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_NotIncludedInvalidBonusPoints() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(1.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_WithStructuredGradingInstructions() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        GradingCriterion criterion = new GradingCriterion();
        criterion.setTitle("Test");

        GradingInstruction gradingInstruction = new GradingInstruction();
        gradingInstruction.setCredits(2);
        gradingInstruction.setGradingScale("Good");
        gradingInstruction.setInstructionDescription("Use this Feedback to test functionality");
        gradingInstruction.setFeedback("This is a test!");
        gradingInstruction.setUsageCount(5);

        criterion.addStructuredGradingInstruction(gradingInstruction);
        textExercise.setGradingCriteria(Set.of(criterion));
        TextExercise actualExercise = request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.OK);

        assertThat(actualExercise.getGradingCriteria()).hasSize(1);
        GradingCriterion testCriterion = GradingCriterionUtil.findGradingCriterionByTitle(actualExercise, "Test");
        assertThat(testCriterion.getStructuredGradingInstructions()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "gradingCriterion")
                .containsExactly(gradingInstruction);
        assertThat(testCriterion.getExercise().getId()).isEqualTo(actualExercise.getId());
        assertThat(testCriterion.getStructuredGradingInstructions()).allMatch(instruction -> instruction.getGradingCriterion().getId().equals(testCriterion.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(textExercise.getId());

        // update certain attributes of text exercise
        String title = "Updated Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);

        // add example submission to exercise
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
        textSubmissionRepository.save(textSubmission);
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setSubmission(textSubmission);
        exampleSubmission.setExercise(textExercise);
        exampleSubmissionRepo.save(exampleSubmission);
        textExercise.addExampleSubmission(exampleSubmission);

        TextExercise updatedTextExercise = request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.OK);

        assertThat(updatedTextExercise.getTitle()).as("text exercise title was correctly updated").isEqualTo(title);
        assertThat(updatedTextExercise.getDifficulty()).as("text exercise difficulty was correctly updated").isEqualTo(difficulty);
        assertThat(updatedTextExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(updatedTextExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(updatedTextExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("courseId was not updated").isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseDueDate() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        final TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final TextSubmission submission1 = ParticipationFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
            textExerciseUtilService.saveTextSubmission(textExercise, submission1, TEST_PREFIX + "student1");
            final TextSubmission submission2 = ParticipationFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
            textExerciseUtilService.saveTextSubmission(textExercise, submission2, TEST_PREFIX + "student2");

            final var participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(textExercise.getId()));
            assertThat(participations).hasSize(2);
            participations.get(0).setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            studentParticipationRepository.saveAll(participations);
        }

        textExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        request.put("/api/text-exercises", textExercise, HttpStatus.OK);

        {
            final var participations = studentParticipationRepository.findByExerciseId(textExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(individualDueDate);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setExerciseIdNull_created() throws Exception {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setId(null);
        textExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_updatingCourseId_asInstructor() throws Exception {
        // Create a text exercise.
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise existingTextExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        // Create a new course with different id.
        Long oldCourseId = course.getId();
        Long newCourseId = oldCourseId + 1L;
        Course newCourse = courseUtilService.createCourse(newCourseId);

        // Assign new course to the text exercise.
        existingTextExercise.setCourse(newCourse);

        // Text exercise update with the new course should fail.
        TextExercise returnedTextExercise = request.putWithResponseBody("/api/text-exercises", existingTextExercise, TextExercise.class, HttpStatus.CONFLICT);
        assertThat(returnedTextExercise).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        // Update certain attributes of text exercise
        String updateTitle = "After";
        DifficultyLevel updateDifficulty = DifficultyLevel.HARD;
        textExercise.setTitle(updateTitle);
        textExercise.setDifficulty(updateDifficulty);

        TextExercise updatedTextExercise = request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.OK);

        assertThat(updatedTextExercise.getTitle()).as("text exercise title was correctly updated").isEqualTo(updateTitle);
        assertThat(updatedTextExercise.getDifficulty()).as("text exercise difficulty was correctly updated").isEqualTo(updateDifficulty);
        assertThat(updatedTextExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(updatedTextExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedTextExercise.getExerciseGroup().getId()).as("exerciseGroupId was not updated").isEqualTo(exerciseGroup.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        request.putWithResponseBody("/api/text-exercises", invalidDates.applyTo(textExercise), TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setCourse(null);

        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_convertFromCourseToExamExercise_badRequest() throws Exception {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);

        textExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_convertFromExamToCourseExercise_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        textExercise.setExerciseGroup(null);

        request.putWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course2);
        textExercise.setChannelName("testchannel" + textExercise.getId());
        var newTextExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        Channel channel = channelRepository.findChannelByExerciseId(newTextExercise.getId());
        assertThat(channel).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseWithExampleSubmissionFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExercise = textExerciseRepository.save(textExercise);
        textExercise.setChannelName("testchannel" + textExercise.getId());
        // Create example submission
        var exampleSubmission = participationUtilService.generateExampleSubmission("Lorem Ipsum", textExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);

        var automaticTextBlock = TextExerciseFactory.generateTextBlock(1, 4, "orem");
        automaticTextBlock.automatic();

        var manualTextBlock = TextExerciseFactory.generateTextBlock(1, 3, "ore");
        manualTextBlock.manual();

        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(manualTextBlock, automaticTextBlock), (TextSubmission) exampleSubmission.getSubmission());

        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(newTextExercise.getExampleSubmissions()).hasSize(1);
        ExampleSubmission newExampleSubmission = newTextExercise.getExampleSubmissions().iterator().next();
        var textBlocks = ((TextSubmission) newExampleSubmission.getSubmission()).getBlocks();
        assertThat(textBlocks).hasSize(2);

        TextBlock manualTextBlockFromImport = textBlocks.stream().filter(tb -> tb.getText().equals(manualTextBlock.getText())).findFirst().orElseThrow();
        assertThat(manualTextBlockFromImport.getId()).isNotEqualTo(manualTextBlock.getId());
        assertTextBlocksHaveSameContent(manualTextBlock, manualTextBlockFromImport);

        TextBlock automaticTextBlockFromImport = textBlocks.stream().filter(tb -> tb.getText().equals(automaticTextBlock.getText())).findFirst().orElseThrow();
        assertThat(automaticTextBlockFromImport.getId()).isNotEqualTo(automaticTextBlock.getId());
        assertTextBlocksHaveSameContent(automaticTextBlock, automaticTextBlockFromImport);
    }

    private static void assertTextBlocksHaveSameContent(TextBlock manualTextBlock, TextBlock manualTextBlockFromImport) {
        assertThat(manualTextBlockFromImport.getType()).isEqualTo(manualTextBlock.getType());
        assertThat(manualTextBlockFromImport.getStartIndex()).isEqualTo(manualTextBlock.getStartIndex());
        assertThat(manualTextBlockFromImport.getEndIndex()).isEqualTo(manualTextBlock.getEndIndex());
        assertThat(manualTextBlockFromImport.getText()).isEqualTo(manualTextBlock.getText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToExam() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);
        textExercise.setDueDate(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setReleaseDate(null);
        textExercise.setExerciseGroup(exerciseGroup1);

        var newTextExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);

        // There should not be created a channel for the imported exam exercise
        Channel channel = channelRepository.findChannelByExerciseId(newTextExercise.getId());
        assertThat(channel).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void importTextExerciseFromCourseToExam_forbidden() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);
        textExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromExamToCourse() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        Course course1 = courseUtilService.addEmptyCourse();
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course1);
        textExercise.setExerciseGroup(null);
        textExercise.setChannelName("test" + textExercise.getId());
        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void importTextExerciseFromExamToCourse_forbidden() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        Course course1 = courseUtilService.addEmptyCourse();
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course1);
        textExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ExerciseGroup exerciseGroup2 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        textExerciseRepository.save(textExercise);
        textExercise.setExerciseGroup(exerciseGroup2);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse_badRequest() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);

        textExercise.setExampleSolutionPublicationDate(ZonedDateTime.now());

        textExercise = textExerciseRepository.save(textExercise);
        textExercise.setCourse(course2);
        textExercise.setChannelName("test-" + textExercise.getId());
        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(newTextExercise.getExampleSolutionPublicationDate()).as("text example solution publication date was correctly set to null in the response").isNull();

        TextExercise newTextExerciseFromDatabase = textExerciseRepository.findById(newTextExercise.getId()).orElseThrow();
        assertThat(newTextExerciseFromDatabase.getExampleSolutionPublicationDate()).as("text example solution publication date was correctly set to null in the database").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllTextExercisesForCourse() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();

        List<TextExercise> textExercises = request.getList("/api/courses/" + course.getId() + "/text-exercises", HttpStatus.OK, TextExercise.class);

        assertThat(textExercises).as("text exercises for course were retrieved").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllTextExercisesForCourse_isNotAtLeastTeachingAssistantInCourse_forbidden() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        request.getList("/api/courses/" + course.getId() + "/text-exercises", HttpStatus.FORBIDDEN, TextExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextExercise_notFound() throws Exception {
        TextExercise textExercise = new TextExercise();
        textExercise.setId(114213211L);

        request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.NOT_FOUND, TextExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextExerciseAsTutor() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        Channel channel = new Channel();
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        channel.setExercise(textExercise);
        channelRepository.save(channel);
        TextExercise textExerciseServer = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);

        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExamTextExerciseAsTutor_forbidden() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, TextExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamTextExerciseAsInstructor() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        TextExercise textExerciseServer = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);
        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
        assertThat(textExerciseServer.getId()).as("Text exercise with the right id was retrieved").isEqualTo(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextExercise_isNotAtleastTeachingAssistantInCourse_forbidden() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);
        request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, TextExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTextExercise_setGradingInstructionFeedbackUsed() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        Channel channel = new Channel();
        channel.setName("testchannel-" + UUID.randomUUID().toString().substring(0, 8));
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setExercise(textExercise);
        channelRepository.save(channel);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> true).orElseThrow());
        feedbackRepository.save(feedback);

        TextExercise receivedTextExercise = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);

        assertThat(receivedTextExercise.isGradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetResultsFromOwningCoursesNotEmpty() throws Exception {
        String courseBaseTitle1 = "testInstructorGetResultsFromOwningCoursesNotEmpty 1";
        String courseBaseTitle2 = "testInstructorGetResultsFromOwningCoursesNotEmpty 2";

        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseBaseTitle1);
        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseBaseTitle2 + "Bachelor");
        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseBaseTitle2 + "Master");

        final var searchText = pageableSearchUtilService.configureSearch(courseBaseTitle1);
        final var resultText = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(searchText));
        assertThat(resultText.getResultsOnPage()).hasSize(1);

        final var searchEssay = pageableSearchUtilService.configureSearch(courseBaseTitle2);
        final var resultEssay = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage()).hasSize(2);

        final var searchNon = pageableSearchUtilService.configureSearch("No course has this name");
        final var resultNon = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(searchNon));
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
        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseTitle);
        textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise(courseTitle + "-Morpork");
        exerciseIntegrationTestService.testCourseAndExamFilters("/api/text-exercises", courseTitle);
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
        TextExercise exercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course);
        exercise.setTitle("LoremIpsum");
        exercise = textExerciseRepository.save(exercise);
        var exerciseId = exercise.getId();

        final var searchTerm = pageableSearchUtilService.configureSearch(exerciseId.toString());
        final var searchResult = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage().stream().filter(result -> result.getId() == exerciseId.intValue())).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningExams() throws Exception {
        textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetResultsFromOwningExamsNotEmpty() throws Exception {
        String exerciseBaseTitle1 = "testInstructorGetResultsFromOwningExamsNotEmpty 1";
        String exerciseBaseTitle2 = "testInstructorGetResultsFromOwningExamsNotEmpty 2";

        textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise(exerciseBaseTitle1);
        textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise(exerciseBaseTitle2 + "Bachelor");
        textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise(exerciseBaseTitle2 + "Master");

        final var searchText = pageableSearchUtilService.configureSearch(exerciseBaseTitle1);
        final var resultText = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(searchText));
        assertThat(resultText.getResultsOnPage()).hasSize(1);

        final var searchEssay = pageableSearchUtilService.configureSearch(exerciseBaseTitle2);
        final var resultEssay = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage()).hasSize(2);

        final var searchNon = pageableSearchUtilService.configureSearch("No exam has this name");
        final var resultNon = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(searchNon));
        assertThat(resultNon.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        String courseTitle = "testAdminGetsResultsFromAllCourses";

        textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseTitle);
        Course otherInstructorsCourse = textExerciseUtilService.addCourseWithOneReleasedTextExercise(courseTitle);
        otherInstructorsCourse.setInstructorGroupName("other-instructors");
        courseRepository.save(otherInstructorsCourse);

        final var search = pageableSearchUtilService.configureSearch(courseTitle);
        final var result = request.getSearchResult("/api/text-exercises", HttpStatus.OK, TextExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_team_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        TextExercise sourceExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        sourceExercise = textExerciseRepository.save(sourceExercise);

        var exerciseToBeImported = new TextExercise();
        exerciseToBeImported.setMode(ExerciseMode.TEAM);

        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exerciseToBeImported);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exerciseToBeImported.setTeamAssignmentConfig(teamAssignmentConfig);
        exerciseToBeImported.setCourse(course2);
        exerciseToBeImported.setMaxPoints(1.0);
        exerciseToBeImported.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 3));

        exerciseToBeImported = request.postWithResponseBody("/api/text-exercises/import/" + sourceExercise.getId(), exerciseToBeImported, TextExercise.class, HttpStatus.CREATED);

        assertThat(exerciseToBeImported.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course2.getId());
        assertThat(exerciseToBeImported.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(exerciseToBeImported.getTeamAssignmentConfig().getMinTeamSize()).isEqualTo(teamAssignmentConfig.getMinTeamSize());
        assertThat(exerciseToBeImported.getTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(teamAssignmentConfig.getMaxTeamSize());
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null)).isEmpty();

        sourceExercise = textExerciseRepository.findById(sourceExercise.getId()).orElseThrow();
        assertThat(sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course1.getId());
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(sourceExercise.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_individual_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();
        TextExercise sourceExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        sourceExercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(sourceExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        sourceExercise.setCourse(course1);

        sourceExercise = textExerciseRepository.save(sourceExercise);
        var team = new Team();
        team.setShortName("testImportTextExercise_individual_modeChange");
        teamRepository.save(sourceExercise, team);

        var exerciseToBeImported = new TextExercise();
        exerciseToBeImported.setMode(ExerciseMode.INDIVIDUAL);
        exerciseToBeImported.setCourse(course2);
        exerciseToBeImported.setMaxPoints(1.0);
        exerciseToBeImported.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 3));

        exerciseToBeImported = request.postWithResponseBody("/api/text-exercises/import/" + sourceExercise.getId(), exerciseToBeImported, TextExercise.class, HttpStatus.CREATED);

        assertThat(exerciseToBeImported.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course2.getId());
        assertThat(exerciseToBeImported.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(exerciseToBeImported.getTeamAssignmentConfig()).isNull();
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null)).isEmpty();

        sourceExercise = textExerciseRepository.findById(sourceExercise.getId()).orElseThrow();
        assertThat(sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course1.getId());
        assertThat(sourceExercise.getMode()).isEqualTo(ExerciseMode.TEAM);
        assertThat(teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismIdenticalLongTexts() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        var longText = """
                Lorem ipsum dolor sit amet, consectetur adipiscing elit.
                Aenean vitae vestibulum metus.
                Cras id fringilla tellus, sed maximus mi.
                Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos.
                Aenean non nulla non ipsum posuere lacinia vel id magna.
                Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Nulla facilisi.
                Sed in urna vitae est tempus pulvinar.
                Nulla vel lacinia purus, sollicitudin congue libero.
                Nulla maximus finibus sapien vel venenatis.
                Proin a lacus massa. Vivamus nulla libero, commodo nec nibh consectetur, aliquam gravida mauris.
                Etiam condimentum sem id purus feugiat molestie.
                Donec malesuada eu diam sed viverra.
                Morbi interdum massa non purus consequat, quis aliquam quam lacinia.
                Suspendisse sem risus, varius et fermentum sed, cursus in nunc.
                Ut malesuada nulla quam, sed condimentum tellus laoreet vel.
                Ut id leo lobortis velit sollicitudin laoreet.
                Duis quis orci ac est placerat lacinia sit amet ut ipsum.
                Quisque a sapien mollis, tempor est sit amet, volutpat est.
                Cras molestie maximus nisi a porta. Nullam efficitur id odio at posuere.
                Duis id feugiat massa. Duis vitae ultrices velit.
                Aenean congue vestibulum ligula, nec eleifend nulla vestibulum nec.
                Praesent eu convallis neque. Nulla facilisi. Suspendisse mattis nisl ac.
                """;

        textExerciseUtilService.createSubmissionForTextExercise(textExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"), longText);
        textExerciseUtilService.createSubmissionForTextExercise(textExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student2"), longText);

        var path = "/api/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, PlagiarismResultDTO.class, plagiarismUtilService.getDefaultPlagiarismOptions());
        assertThat(result.plagiarismResult().getComparisons()).hasSize(1);
        assertThat(result.plagiarismResult().getExercise().getId()).isEqualTo(textExercise.getId());

        PlagiarismComparison<TextSubmissionElement> comparison = (PlagiarismComparison<TextSubmissionElement>) result.plagiarismResult().getComparisons().iterator().next();
        // Both submissions compared consist of 4 words (= 4 tokens). JPlag seems to be off by 1
        // when counting the length of a match. This is why it calculates a similarity of 3/4 = 75%
        // instead of 4/4 = 100% (5 words ==> 80%, 100 words ==> 99%, etc.). Therefore, we use a rather
        // high offset here to compensate this issue.
        // TODO: Reduce the offset once this issue is fixed in JPlag
        assertThat(comparison.getSimilarity()).isEqualTo(100.0, Offset.offset(1.0));
        assertThat(comparison.getStatus()).isEqualTo(PlagiarismStatus.NONE);
        assertThat(comparison.getMatches()).hasSize(1);

        // verify plagiarism result stats
        var stats = result.plagiarismResultStats();
        assertThat(stats.numberOfDetectedSubmissions()).isEqualTo(2);
        assertThat(stats.averageSimilarity()).isEqualTo(100.0, Offset.offset(1.0));
        assertThat(stats.maximalSimilarity()).isEqualTo(100.0, Offset.offset(1.0));

        var plagiarismStatusDto = new PlagiarismComparisonStatusDTO(CONFIRMED);
        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + comparison.getId() + "/status", plagiarismStatusDto, HttpStatus.OK);
        assertThat(plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparison.getId()).getStatus()).isEqualTo(PlagiarismStatus.CONFIRMED);

        plagiarismStatusDto = new PlagiarismComparisonStatusDTO(DENIED);
        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + comparison.getId() + "/status", plagiarismStatusDto, HttpStatus.OK);
        assertThat(plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparison.getId()).getStatus()).isEqualTo(DENIED);

        plagiarismStatusDto = new PlagiarismComparisonStatusDTO(NONE);
        request.put("/api/courses/" + course.getId() + "/plagiarism-comparisons/" + comparison.getId() + "/status", plagiarismStatusDto, HttpStatus.OK);
        assertThat(plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(comparison.getId()).getStatus()).isEqualTo(PlagiarismStatus.NONE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismIdenticalShortTexts() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        var shortText = "Lorem Ipsum Foo Bar";
        textExerciseUtilService.createSubmissionForTextExercise(textExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"), shortText);
        textExerciseUtilService.createSubmissionForTextExercise(textExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student2"), shortText);

        var path = "/api/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        request.get(path, HttpStatus.BAD_REQUEST, TextPlagiarismResult.class, plagiarismUtilService.getPlagiarismOptions(50, 0, 5));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismNoSubmissions() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        var path = "/api/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        request.get(path, HttpStatus.BAD_REQUEST, TextPlagiarismResult.class, plagiarismUtilService.getDefaultPlagiarismOptions());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarism_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        request.get("/api/text-exercises/" + textExercise.getId() + "/check-plagiarism", HttpStatus.FORBIDDEN, TextPlagiarismResult.class,
                plagiarismUtilService.getDefaultPlagiarismOptions());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResult() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        TextPlagiarismResult expectedResult = textExerciseUtilService.createTextPlagiarismResultForExercise(textExercise);

        var result = request.get("/api/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.OK, PlagiarismResultDTO.class);
        assertThat(result.plagiarismResult().getId()).isEqualTo(expectedResult.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResultWithoutResult() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        var result = request.get("/api/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.OK, String.class);
        assertThat(result).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResultWithoutExercise() throws Exception {
        TextPlagiarismResult result = request.get("/api/text-exercises/" + 10000000 + "/plagiarism-result", HttpStatus.NOT_FOUND, TextPlagiarismResult.class);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResult_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        request.get("/api/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(textExercise, TEST_PREFIX + "instructor1");

        // change grading instruction score
        GradingCriterion toUpdate = GradingCriterionUtil.findAnyWhere(gradingCriteria, criterion -> !criterion.getStructuredGradingInstructions().isEmpty()).orElseThrow();
        toUpdate.getStructuredGradingInstructions().stream().findFirst().orElseThrow().setCredits(3);
        gradingCriteria.removeIf(criterion -> criterion != toUpdate);
        textExercise.setGradingCriteria(gradingCriteria);

        TextExercise updatedTextExercise = request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", textExercise,
                TextExercise.class, HttpStatus.OK);
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedTextExercise);
        assertThat(GradingCriterionUtil.findAnyInstructionWhere(updatedTextExercise.getGradingCriteria(), instruction -> instruction.getCredits() == 3)).isPresent();
        assertThat(updatedResults.get(0).getScore()).isEqualTo(60);
        assertThat(updatedResults.get(0).getFeedbacks().get(0).getCredits()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExerciseWithExampleSubmission() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        gradingCriteria.remove(1);
        textExercise.setGradingCriteria(gradingCriteria);

        // Create example submission
        Set<ExampleSubmission> exampleSubmissionSet = new HashSet<>();
        var exampleSubmission = participationUtilService.generateExampleSubmission("text", textExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        TextSubmission textSubmission = (TextSubmission) participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        textSubmission.setExampleSubmission(true);
        Result result = textSubmission.getLatestResult();
        result.setExampleResult(true);
        textSubmission.addResult(result);
        textSubmissionRepository.save(textSubmission);
        exampleSubmissionSet.add(exampleSubmission);
        textExercise.setExampleSubmissions(exampleSubmissionSet);

        request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", textExercise, TextExercise.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_shouldDeleteFeedbacks() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(textExercise, TEST_PREFIX + "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.removeIf(criterion -> criterion.getTitle() == null);
        textExercise.setGradingCriteria(gradingCriteria);

        TextExercise updatedTextExercise = request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate" + "?deleteFeedback=true", textExercise,
                TextExercise.class, HttpStatus.OK);
        List<Result> updatedResults = participationUtilService.getResultsForExercise(updatedTextExercise);
        assertThat(updatedTextExercise.getGradingCriteria()).hasSize(2);
        assertThat(updatedResults.get(0).getScore()).isZero();
        assertThat(updatedResults.get(0).getFeedbacks()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate", textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextExercise textExerciseToBeConflicted = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExerciseToBeConflicted.setId(123456789L);
        textExerciseRepository.save(textExerciseToBeConflicted);

        request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate", textExerciseToBeConflicted, TextExercise.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_notFound() throws Exception {
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        request.putWithResponseBody("/api/text-exercises/" + 123456789 + "/re-evaluate", textExercise, TextExercise.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setId(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(3));
        textExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);

        textExercise.setReleaseDate(baseTime.plusHours(3));
        textExercise.setDueDate(null);
        textExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setId(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        textExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        textExercise.setChannelName("test");

        var result = request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        textExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        textExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        result = request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetTextExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetTextExercise_exampleSolutionVisibility(true, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetTextExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetTextExercise_exampleSolutionVisibility(false, "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_setGradingInstructionForCopiedFeedback() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = courseUtilService.addEmptyCourse();
        Course course2 = courseUtilService.addEmptyCourse();

        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExercise = textExerciseRepository.save(textExercise);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        GradingInstruction gradingInstruction = GradingCriterionUtil.findAnyInstructionWhere(gradingCriteria, instruction -> instruction.getFeedback() != null).orElseThrow();

        // Create example submission
        var exampleSubmission = participationUtilService.generateExampleSubmission("text", textExercise, true);
        exampleSubmission = participationUtilService.addExampleSubmission(exampleSubmission);
        participationUtilService.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        var submission = textSubmissionRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(exampleSubmission.getSubmission().getId());

        Feedback feedback = ParticipationFactory.generateFeedback().get(0);
        feedback.setGradingInstruction(gradingInstruction);
        participationUtilService.addFeedbackToResult(feedback, Objects.requireNonNull(submission.getLatestResult()));

        textExercise.setCourse(course2);
        textExercise.setChannelName("test" + UUID.randomUUID().toString().substring(0, 8));
        var importedTextExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);

        assertThat(textExerciseRepository.findById(importedTextExercise.getId())).isPresent();

        var importedExampleSubmission = importedTextExercise.getExampleSubmissions().stream().findFirst().orElseThrow();
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

        var importedTextExerciseFromDB = textExerciseRepository.findWithExampleSubmissionsAndResultsById(importedTextExercise.getId()).orElseThrow();
        var importedFeedbackGradingInstructionFromDb = importedTextExerciseFromDB.getExampleSubmissions().stream().findFirst().orElseThrow().getSubmission().getLatestResult()
                .getFeedbacks().get(0).getGradingInstruction();

        assertThat(importedFeedbackGradingInstructionFromDb.getGradingCriterion().getId()).isNotEqualTo(gradingInstruction.getGradingCriterion().getId());

    }

    private void testGetTextExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        final TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        // Utility function to avoid duplication
        Function<Course, TextExercise> textExerciseGetter = c -> (TextExercise) c.getExercises().stream().filter(e -> e.getId().equals(textExercise.getId())).findAny()
                .orElseThrow();

        textExercise.setExampleSolution("Sample<br>solution");

        if (isStudent) {
            participationUtilService.createAndSaveParticipationForExercise(textExercise, username);
        }

        // Test example solution publication date not set.
        textExercise.setExampleSolutionPublicationDate(null);
        textExerciseRepository.save(textExercise);

        CourseForDashboardDTO courseForDashboard = request.get("/api/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        TextExercise textExerciseFromApi = textExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(textExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(textExerciseFromApi.getExampleSolution()).isEqualTo(textExercise.getExampleSolution());
        }

        // Test example solution publication date in the past.
        textExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().minusHours(1));
        textExerciseRepository.save(textExercise);

        courseForDashboard = request.get("/api/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        textExerciseFromApi = textExerciseGetter.apply(course);

        assertThat(textExerciseFromApi.getExampleSolution()).isEqualTo(textExercise.getExampleSolution());

        // Test example solution publication date in the future.
        textExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        textExerciseRepository.save(textExercise);

        courseForDashboard = request.get("/api/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        textExerciseFromApi = textExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(textExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(textExerciseFromApi.getExampleSolution()).isEqualTo(textExercise.getExampleSolution());
        }
    }
}
