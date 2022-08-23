package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class TextExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private DatabaseUtilService databaseUtilService;

    @Autowired
    private TextClusterRepository textClusterRepository;

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
    private ExerciseIntegrationTestUtils exerciseIntegrationTestUtils;

    @BeforeEach
    void initTestCase() {
        database.addUsers(2, 1, 0, 1);
        database.addInstructor("other-instructors", "instructorother");
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void submitEnglishTextExercise() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This Submission is written in English", Language.ENGLISH, false);
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", null, Participation.class);
        textSubmission = request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class);

        Optional<TextSubmission> result = textSubmissionRepository.findById(textSubmission.getId());
        assertThat(result).isPresent();
        result.ifPresent(submission -> assertThat(submission.getLanguage()).isEqualTo(Language.ENGLISH));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteTextExerciseWithSubmissionWithTextBlocksAndClusters() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmission(textExercise, textSubmission, "student1");
        int submissionCount = 5;
        int submissionSize = 4;
        var textBlocks = textExerciseUtilService.generateTextBlocks(submissionCount * submissionSize);
        int[] clusterSizes = { 4, 5, 10, 1 };
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(textBlocks, clusterSizes, textExercise);
        textClusterRepository.saveAll(clusters);
        database.addAndSaveTextBlocksToTextSubmission(textBlocks, textSubmission);

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findById(textExercise.getId())).as("text exercise was deleted").isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteExamTextExercise() throws Exception {
        TextExercise textExercise = database.addCourseExamExerciseGroupWithOneTextExercise();

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteTextExercise_notFound() throws Exception {
        TextExercise textExercise = new TextExercise();
        textExercise.setId(114213211L);

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        String title = "New Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;

        textExercise.setId(null);
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);

        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);

        assertThat(newTextExercise.getTitle()).as("text exercise title was correctly set").isEqualTo(title);
        assertThat(newTextExercise.getDifficulty()).as("text exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newTextExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(newTextExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(newTextExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("exerciseGroupId was set correctly").isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setExerciseTitleNull_badRequest() throws Exception {
        TextExercise textExercise = new TextExercise();

        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setAssessmentDueDateWithoutExerciseDueDate_badRequest() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setId(null);
        textExercise.setDueDate(null);

        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        textExercise.setId(null);

        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);

        String title = "New Exam Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);

        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);

        assertThat(newTextExercise.getTitle()).as("text exercise title was correctly set").isEqualTo(title);
        assertThat(newTextExercise.getDifficulty()).as("text exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(newTextExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(newTextExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(newTextExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam_datesSet() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        ZonedDateTime someMoment = ZonedDateTime.of(2000, 6, 15, 0, 0, 0, 0, ZoneId.of("Z"));
        String title = "New Exam Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);
        textExercise.setDueDate(someMoment);
        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
        assertThat(exerciseGroup.getExercises()).doesNotContain(textExercise);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        request.postWithResponseBody("/api/text-exercises/", invalidDates.applyTo(textExercise), TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExercise.setCourse(exerciseGroup.getExam().getCourse());
        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(null);

        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_InvalidMaxScore() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(1.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_NotIncludedInvalidBonusPoints() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(1.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(textExercise.getId());

        // update certain attributes of text exercise
        String title = "Updated Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);

        // add example submission to exercise
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
        textSubmissionRepository.save(textSubmission);
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setSubmission(textSubmission);
        exampleSubmission.setExercise(textExercise);
        exampleSubmissionRepo.save(exampleSubmission);
        textExercise.addExampleSubmission(exampleSubmission);

        TextExercise updatedTextExercise = request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.OK);

        assertThat(updatedTextExercise.getTitle()).as("text exercise title was correctly updated").isEqualTo(title);
        assertThat(updatedTextExercise.getDifficulty()).as("text exercise difficulty was correctly updated").isEqualTo(difficulty);
        assertThat(updatedTextExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(updatedTextExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(updatedTextExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("courseId was not updated").isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseDueDate() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        final TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final TextSubmission submission1 = ModelFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
            databaseUtilService.saveTextSubmission(textExercise, submission1, "student1");
            final TextSubmission submission2 = ModelFactory.generateTextSubmission("Lorem Ipsum Foo Bar", Language.ENGLISH, true);
            databaseUtilService.saveTextSubmission(textExercise, submission2, "student2");

            final var participations = studentParticipationRepository.findByExerciseId(textExercise.getId());
            assertThat(participations).hasSize(2);
            participations.get(0).setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            studentParticipationRepository.saveAll(participations);
        }

        textExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        request.put("/api/text-exercises/", textExercise, HttpStatus.OK);

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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setExerciseIdNull_created() throws Exception {
        Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setId(null);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_updatingCourseId_asInstructor() throws Exception {
        // Create a text exercise.
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise existingTextExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        // Create a new course with different id.
        Long oldCourseId = course.getId();
        Long newCourseId = oldCourseId + 1L;
        Course newCourse = databaseUtilService.createCourse(newCourseId);

        // Assign new course to the text exercise.
        existingTextExercise.setCourse(newCourse);

        // Text exercise update with the new course should fail.
        TextExercise returnedTextExercise = request.putWithResponseBody("/api/text-exercises", existingTextExercise, TextExercise.class, HttpStatus.CONFLICT);
        assertThat(returnedTextExercise).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        // Update certain attributes of text exercise
        String updateTitle = "After";
        DifficultyLevel updateDifficulty = DifficultyLevel.HARD;
        textExercise.setTitle(updateTitle);
        textExercise.setDifficulty(updateDifficulty);

        TextExercise updatedTextExercise = request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.OK);

        assertThat(updatedTextExercise.getTitle()).as("text exercise title was correctly updated").isEqualTo(updateTitle);
        assertThat(updatedTextExercise.getDifficulty()).as("text exercise difficulty was correctly updated").isEqualTo(updateDifficulty);
        assertThat(updatedTextExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(updatedTextExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedTextExercise.getExerciseGroup().getId()).as("exerciseGroupId was not updated").isEqualTo(exerciseGroup.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        request.putWithResponseBody("/api/text-exercises/", invalidDates.applyTo(textExercise), TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        Course course = database.addCourseWithOneReleasedTextExercise();
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setCourse(null);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_convertFromCourseToExamExercise_badRequest() throws Exception {
        Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);

        textExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTextExercise_convertFromExamToCourseExercise_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        textExercise.setExerciseGroup(null);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course2);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseWithExampleSubmissionFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);

        // Create example submission
        var exampleSubmission = database.generateExampleSubmission("Lorem Ipsum", textExercise, true);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);

        var automaticTextBlock = ModelFactory.generateTextBlock(1, 4, "orem");
        automaticTextBlock.automatic();

        var manualTextBlock = ModelFactory.generateTextBlock(1, 3, "ore");
        manualTextBlock.manual();

        database.addAndSaveTextBlocksToTextSubmission(Set.of(manualTextBlock, automaticTextBlock), (TextSubmission) exampleSubmission.getSubmission());

        database.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(newTextExercise.getExampleSubmissions()).hasSize(1);
        ExampleSubmission newExampleSubmission = newTextExercise.getExampleSubmissions().iterator().next();
        var textBlocks = ((TextSubmission) newExampleSubmission.getSubmission()).getBlocks();
        assertThat(textBlocks).hasSize(2);

        TextBlock manualTextBlockFromImport = textBlocks.stream().filter(tb -> tb.getText().equals(manualTextBlock.getText())).findFirst().get();
        assertThat(manualTextBlockFromImport.getId()).isNotEqualTo(manualTextBlock.getId());
        assertTextBlocksHaveSameContent(manualTextBlock, manualTextBlockFromImport);

        TextBlock automaticTextBlockFromImport = textBlocks.stream().filter(tb -> tb.getText().equals(automaticTextBlock.getText())).findFirst().get();
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToExam() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);
        textExercise.setDueDate(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setReleaseDate(null);
        textExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "TA")
    void importTextExerciseFromCourseToExam_forbidden() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);
        textExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromExamToCourse() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        Course course1 = database.addEmptyCourse();
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course1);
        textExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "TA")
    void importTextExerciseFromExamToCourse_forbidden() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        Course course1 = database.addEmptyCourse();
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course1);
        textExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        ExerciseGroup exerciseGroup2 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        textExerciseRepository.save(textExercise);
        textExercise.setExerciseGroup(exerciseGroup2);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse_badRequest() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importTextExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);

        textExercise.setExampleSolutionPublicationDate(ZonedDateTime.now());

        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course2);

        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(newTextExercise.getExampleSolutionPublicationDate()).as("text example solution publication date was correctly set to null in the response").isNull();

        TextExercise newTextExerciseFromDatabase = textExerciseRepository.findById(newTextExercise.getId()).get();
        assertThat(newTextExerciseFromDatabase.getExampleSolutionPublicationDate()).as("text example solution publication date was correctly set to null in the database").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getAllTextExercisesForCourse() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();

        List<TextExercise> textExercises = request.getList("/api/courses/" + course.getId() + "/text-exercises/", HttpStatus.OK, TextExercise.class);

        assertThat(textExercises).as("text exercises for course were retrieved").hasSize(1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getAllTextExercisesForCourse_isNotAtLeastTeachingAssistantInCourse_forbidden() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        request.getList("/api/courses/" + course.getId() + "/text-exercises/", HttpStatus.FORBIDDEN, TextExercise.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextExercise_notFound() throws Exception {
        TextExercise textExercise = new TextExercise();
        textExercise.setId(114213211L);

        request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.NOT_FOUND, TextExercise.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextExerciseAsTutor() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        TextExercise textExerciseServer = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);

        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getExamTextExerciseAsTutor_forbidden() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, TextExercise.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getExamTextExerciseAsInstructor() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        TextExercise textExerciseServer = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);
        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
        assertThat(textExercise.getId()).as("Text exercise with the right id was retrieved").isEqualTo(textExercise.getId());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextExercise_isNotAtleastTeachingAssistantInCourse_forbidden() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);
        request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, TextExercise.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetTextExercise_setGradingInstructionFeedbackUsed() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(gradingCriteria.get(0).getStructuredGradingInstructions().get(0));
        feedbackRepository.save(feedback);

        TextExercise receivedTextExercise = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);

        assertThat(receivedTextExercise.isGradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testTriggerAutomaticAssessment() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        request.postWithoutLocation("/api/admin/text-exercises/" + textExercise.getId() + "/trigger-automatic-assessment", null, HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(username = "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        final var search = database.configureSearch("");
        final var result = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetResultsFromOwningCoursesNotEmpty() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        database.addCourseWithOneReleasedTextExercise("Essay Bachelor");
        database.addCourseWithOneReleasedTextExercise("Essay Master");

        final var searchText = database.configureSearch("Text");
        final var resultText = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchText));
        assertThat(resultText.getResultsOnPage()).hasSize(1);

        final var searchEssay = database.configureSearch("Essay");
        final var resultEssay = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage()).hasSize(2);

        final var searchNon = database.configureSearch("Non");
        final var resultNon = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchNon));
        assertThat(resultNon.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCourseAndExamFiltersAsInstructor() throws Exception {
        database.addCourseWithOneReleasedTextExercise("Ankh");
        database.addCourseExamExerciseGroupWithOneTextExercise("Ankh-Morpork");
        exerciseIntegrationTestUtils.testCourseAndExamFilters("/api/text-exercises");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCourseAndExamFiltersAsAdmin() throws Exception {
        database.addCourseWithOneReleasedTextExercise("Ankh");
        database.addCourseExamExerciseGroupWithOneTextExercise("Ankh-Morpork");
        exerciseIntegrationTestUtils.testCourseAndExamFilters("/api/text-exercises");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testInstructorSearchTermMatchesId() throws Exception {
        database.resetDatabase();
        database.addUsers(1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminSearchTermMatchesId() throws Exception {
        database.resetDatabase();
        database.addUsers(1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    private void testSearchTermMatchesId() throws Exception {
        final Course course = database.addEmptyCourse();
        final var now = ZonedDateTime.now();
        TextExercise exercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course);
        exercise.setTitle("LoremIpsum");
        exercise = textExerciseRepository.save(exercise);

        final var searchTerm = database.configureSearch(exercise.getId().toString());
        final var searchResult = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningExams() throws Exception {
        database.addCourseExamExerciseGroupWithOneTextExercise();
        final var search = database.configureSearch("");
        final var result = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetResultsFromOwningExamsNotEmpty() throws Exception {
        database.addCourseExamExerciseGroupWithOneTextExercise("Text");
        database.addCourseExamExerciseGroupWithOneTextExercise("Essay Bachelor");
        database.addCourseExamExerciseGroupWithOneTextExercise("Essay Master");

        final var searchText = database.configureSearch("Text");
        final var resultText = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchText));
        assertThat(resultText.getResultsOnPage()).hasSize(1);

        final var searchEssay = database.configureSearch("Essay");
        final var resultEssay = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage()).hasSize(2);

        final var searchNon = database.configureSearch("Non");
        final var resultNon = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchNon));
        assertThat(resultNon.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        database.addCourseInOtherInstructionGroupAndExercise("Text");

        final var search = database.configureSearch("Text");
        final var result = request.get("/api/text-exercises", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_team_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        TextExercise sourceExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
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

        exerciseToBeImported = request.postWithResponseBody("/api/text-exercises/import/" + sourceExercise.getId(), exerciseToBeImported, TextExercise.class, HttpStatus.CREATED);

        assertEquals(course2.getId(), exerciseToBeImported.getCourseViaExerciseGroupOrCourseMember().getId(), course2.getId());
        assertEquals(ExerciseMode.TEAM, exerciseToBeImported.getMode());
        assertEquals(teamAssignmentConfig.getMinTeamSize(), exerciseToBeImported.getTeamAssignmentConfig().getMinTeamSize());
        assertEquals(teamAssignmentConfig.getMaxTeamSize(), exerciseToBeImported.getTeamAssignmentConfig().getMaxTeamSize());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null).size());

        sourceExercise = textExerciseRepository.findById(sourceExercise.getId()).get();
        assertEquals(course1.getId(), sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertEquals(ExerciseMode.INDIVIDUAL, sourceExercise.getMode());
        assertNull(sourceExercise.getTeamAssignmentConfig());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null).size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_individual_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        TextExercise sourceExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        sourceExercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(sourceExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        sourceExercise.setCourse(course1);

        sourceExercise = textExerciseRepository.save(sourceExercise);
        teamRepository.save(sourceExercise, new Team());

        var exerciseToBeImported = new TextExercise();
        exerciseToBeImported.setMode(ExerciseMode.INDIVIDUAL);
        exerciseToBeImported.setCourse(course2);
        exerciseToBeImported.setMaxPoints(1.0);

        exerciseToBeImported = request.postWithResponseBody("/api/text-exercises/import/" + sourceExercise.getId(), exerciseToBeImported, TextExercise.class, HttpStatus.CREATED);

        assertEquals(course2.getId(), exerciseToBeImported.getCourseViaExerciseGroupOrCourseMember().getId(), course2.getId());
        assertEquals(ExerciseMode.INDIVIDUAL, exerciseToBeImported.getMode());
        assertNull(exerciseToBeImported.getTeamAssignmentConfig());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null).size());

        sourceExercise = textExerciseRepository.findById(sourceExercise.getId()).get();
        assertEquals(course1.getId(), sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertEquals(ExerciseMode.TEAM, sourceExercise.getMode());
        assertEquals(1, teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null).size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismIdenticalLongTexts() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
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

        database.createSubmissionForTextExercise(textExercise, database.getUserByLogin("student1"), longText);
        database.createSubmissionForTextExercise(textExercise, database.getUserByLogin("student2"), longText);

        var path = "/api/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, TextPlagiarismResult.class, database.getDefaultPlagiarismOptions());
        assertThat(result.getComparisons()).hasSize(1);
        assertThat(result.getExercise().getId()).isEqualTo(textExercise.getId());

        PlagiarismComparison<TextSubmissionElement> comparison = result.getComparisons().iterator().next();
        // Both submissions compared consist of 4 words (= 4 tokens). JPlag seems to be off by 1
        // when counting the length of a match. This is why it calculates a similarity of 3/4 = 75%
        // instead of 4/4 = 100% (5 words ==> 80%, 100 words ==> 99%, etc.). Therefore, we use a rather
        // high offset here to compensate this issue.
        // TODO: Reduce the offset once this issue is fixed in JPlag
        assertThat(comparison.getSimilarity()).isEqualTo(100.0, Offset.offset(1.0));
        assertThat(comparison.getStatus()).isEqualTo(PlagiarismStatus.NONE);
        assertThat(comparison.getMatches()).hasSize(1);

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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismIdenticalShortTexts() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        var shortText = "Lorem Ipsum Foo Bar";
        database.createSubmissionForTextExercise(textExercise, database.getUserByLogin("student1"), shortText);
        database.createSubmissionForTextExercise(textExercise, database.getUserByLogin("student2"), shortText);

        var path = "/api/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, TextPlagiarismResult.class, database.getPlagiarismOptions(50D, 0, 5));
        assertThat(result.getComparisons()).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismNoSubmissions() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        var path = "/api/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, TextPlagiarismResult.class, database.getDefaultPlagiarismOptions());
        assertThat(result.getComparisons()).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarism_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        request.get("/api/text-exercises/" + textExercise.getId() + "/check-plagiarism", HttpStatus.FORBIDDEN, TextPlagiarismResult.class, database.getDefaultPlagiarismOptions());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResult() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        TextPlagiarismResult expectedResult = database.createTextPlagiarismResultForExercise(textExercise);

        TextPlagiarismResult result = request.get("/api/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.OK, TextPlagiarismResult.class);
        assertThat(result.getId()).isEqualTo(expectedResult.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResultWithoutResult() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        var result = request.get("/api/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.OK, String.class);
        assertThat(result).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResultWithoutExercise() throws Exception {
        TextPlagiarismResult result = request.get("/api/text-exercises/" + 1 + "/plagiarism-result", HttpStatus.NOT_FOUND, TextPlagiarismResult.class);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResult_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        request.get("/api/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        database.addAssessmentWithFeedbackWithGradingInstructionsForExercise(textExercise, "instructor1");

        // change grading instruction score
        gradingCriteria.get(0).getStructuredGradingInstructions().get(0).setCredits(3);
        gradingCriteria.remove(1);
        textExercise.setGradingCriteria(gradingCriteria);

        TextExercise updatedTextExercise = request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", textExercise,
                TextExercise.class, HttpStatus.OK);
        List<Result> updatedResults = database.getResultsForExercise(updatedTextExercise);
        assertThat(updatedTextExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0).getCredits()).isEqualTo(3);
        assertThat(updatedResults.get(0).getScore()).isEqualTo(60);
        assertThat(updatedResults.get(0).getFeedbacks().get(0).getCredits()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExerciseWithExampleSubmission() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        gradingCriteria.remove(1);
        textExercise.setGradingCriteria(gradingCriteria);

        // Create example submission
        Set<ExampleSubmission> exampleSubmissionSet = new HashSet<>();
        var exampleSubmission = database.generateExampleSubmission("text", textExercise, true);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);
        TextSubmission textSubmission = (TextSubmission) database.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_shouldDeleteFeedbacks() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        database.addAssessmentWithFeedbackWithGradingInstructionsForExercise(textExercise, "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.remove(1);
        gradingCriteria.remove(0);
        textExercise.setGradingCriteria(gradingCriteria);

        TextExercise updatedTextExercise = request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate" + "?deleteFeedback=true", textExercise,
                TextExercise.class, HttpStatus.OK);
        List<Result> updatedResults = database.getResultsForExercise(updatedTextExercise);
        assertThat(updatedTextExercise.getGradingCriteria()).hasSize(1);
        assertThat(updatedResults.get(0).getScore()).isZero();
        assertThat(updatedResults.get(0).getFeedbacks()).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        course.setInstructorGroupName("test");
        courseRepository.save(course);

        request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate", textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        TextExercise textExerciseToBeConflicted = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExerciseToBeConflicted.setId(123456789L);
        textExerciseRepository.save(textExerciseToBeConflicted);

        request.putWithResponseBody("/api/text-exercises/" + textExercise.getId() + "/re-evaluate", textExerciseToBeConflicted, TextExercise.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateTextExercise_notFound() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        request.putWithResponseBody("/api/text-exercises/" + 123456789 + "/re-evaluate", textExercise, TextExercise.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setId(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(3));
        textExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);

        textExercise.setReleaseDate(baseTime.plusHours(3));
        textExercise.setDueDate(null);
        textExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTextExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        textExercise.setId(null);
        textExercise.setAssessmentDueDate(null);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        textExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);

        var result = request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        textExercise.setReleaseDate(baseTime.plusHours(1));
        textExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        textExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);

        result = request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetTextExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetTextExercise_exampleSolutionVisibility(true, "student1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetTextExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetTextExercise_exampleSolutionVisibility(false, "instructor1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testImportTextExercise_setGradingInstructionForCopiedFeedback() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();

        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExercise = textExerciseRepository.save(textExercise);
        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        GradingInstruction gradingInstruction = gradingCriteria.get(0).getStructuredGradingInstructions().get(0);
        assertThat(gradingInstruction.getFeedback()).as("Test feedback should have student readable feedback").isNotEmpty();

        // Create example submission
        var exampleSubmission = database.generateExampleSubmission("text", textExercise, true);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);
        database.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        var submission = textSubmissionRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(exampleSubmission.getSubmission().getId());

        Feedback feedback = ModelFactory.generateFeedback().get(0);
        feedback.setGradingInstruction(gradingInstruction);
        database.addFeedbackToResult(feedback, Objects.requireNonNull(submission.getLatestResult()));

        textExercise.setCourse(course2);
        var importedTextExercise = request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);

        assertThat(textExerciseRepository.findById(importedTextExercise.getId())).isPresent();

        var importedExampleSubmission = importedTextExercise.getExampleSubmissions().stream().findFirst().get();
        GradingInstruction importedFeedbackGradingInstruction = importedExampleSubmission.getSubmission().getLatestResult().getFeedbacks().get(0).getGradingInstruction();
        assertThat(importedFeedbackGradingInstruction).isNotNull();

        // Copy and original should have the same data but not the same ids.
        assertThat(importedFeedbackGradingInstruction.getId()).isNotEqualTo(gradingInstruction.getId());
        assertThat(importedFeedbackGradingInstruction.getGradingCriterion()).isNull();  // To avoid infinite recursion when serializing to JSON.
        assertThat(importedFeedbackGradingInstruction.getFeedback()).isEqualTo(gradingInstruction.getFeedback());
        assertThat(importedFeedbackGradingInstruction.getGradingScale()).isEqualTo(gradingInstruction.getGradingScale());
        assertThat(importedFeedbackGradingInstruction.getInstructionDescription()).isEqualTo(gradingInstruction.getInstructionDescription());
        assertThat(importedFeedbackGradingInstruction.getCredits()).isEqualTo(gradingInstruction.getCredits());
        assertThat(importedFeedbackGradingInstruction.getUsageCount()).isEqualTo(gradingInstruction.getUsageCount());

        var importedTextExerciseFromDB = textExerciseRepository.findByIdWithExampleSubmissionsAndResults(importedTextExercise.getId()).get();
        var importedFeedbackGradingInstructionFromDb = importedTextExerciseFromDB.getExampleSubmissions().stream().findFirst().get().getSubmission().getLatestResult()
                .getFeedbacks().get(0).getGradingInstruction();

        assertThat(importedFeedbackGradingInstructionFromDb.getGradingCriterion().getId()).isNotEqualTo(gradingInstruction.getGradingCriterion().getId());

    }

    private void testGetTextExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        Course course = database.addCourseWithOneReleasedTextExercise();
        final TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        // Utility function to avoid duplication
        Function<Course, TextExercise> textExerciseGetter = c -> (TextExercise) c.getExercises().stream().filter(e -> e.getId().equals(textExercise.getId())).findAny().get();

        textExercise.setExampleSolution("Sample<br>solution");

        if (isStudent) {
            database.createAndSaveParticipationForExercise(textExercise, username);
        }

        // Test example solution publication date not set.
        textExercise.setExampleSolutionPublicationDate(null);
        textExerciseRepository.save(textExercise);

        course = request.get("/api/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK, Course.class);
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

        course = request.get("/api/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK, Course.class);
        textExerciseFromApi = textExerciseGetter.apply(course);

        assertThat(textExerciseFromApi.getExampleSolution()).isEqualTo(textExercise.getExampleSolution());

        // Test example solution publication date in the future.
        textExercise.setExampleSolutionPublicationDate(ZonedDateTime.now().plusHours(1));
        textExerciseRepository.save(textExercise);

        course = request.get("/api/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK, Course.class);
        textExerciseFromApi = textExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(textExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(textExerciseFromApi.getExampleSolution()).isEqualTo(textExercise.getExampleSolution());
        }
    }
}
