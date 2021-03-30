package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TeamAssignmentConfig;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

public class TextExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private TeamRepository teamRepository;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 1, 1);
        database.addInstructor("other-instructors", "instructorother");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void submitEnglishTextExercise() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This Submission is written in English", Language.ENGLISH, false);
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        request.postWithResponseBody("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/participations", null, Participation.class);
        textSubmission = request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class);

        Optional<TextSubmission> result = textSubmissionRepository.findById(textSubmission.getId());
        assertThat(result.isPresent()).isEqualTo(true);
        result.ifPresent(submission -> assertThat(submission.getLanguage()).isEqualTo(Language.ENGLISH));

    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void deleteTextExerciseWithSubmissionWithTextBlocksAndClusters() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void deleteExamTextExercise() throws Exception {
        TextExercise textExercise = database.addCourseExamExerciseGroupWithOneTextExercise();

        request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK);
        assertThat(textExerciseRepository.findAll().isEmpty());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createTextExercise() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);

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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createTextExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);

        String title = "New Exam Text Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        textExercise.setTitle(title);
        textExercise.setDifficulty(difficulty);

        TextExercise newTextExercise = request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.CREATED);

        assertThat(newTextExercise.getTitle()).as("text exercise title was correctly set").isEqualTo(title);
        assertThat(newTextExercise.getDifficulty()).as("text exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(!newTextExercise.isCourseExercise()).as("course was not set for exam exercise");
        assertThat(newTextExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(newTextExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createTextExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExercise.setCourse(exerciseGroup.getExam().getCourse());

        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createTextExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(null);

        request.postWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createTextExercise_InvalidMaxScore() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        textExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createTextExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(1.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createTextExercise_NotIncludedInvalidBonusPoints() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(1.0);
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/text-exercises", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateTextExercise() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateTextExerciseForExam() throws Exception {
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
        assertThat(!updatedTextExercise.isCourseExercise()).as("course was not set for exam exercise");
        assertThat(updatedTextExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedTextExercise.getExerciseGroup().getId()).as("exerciseGroupId was not updated").isEqualTo(exerciseGroup.getId());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateTextExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        Course course = database.addCourseWithOneReleasedTextExercise();
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        textExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateTextExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        textExercise.setCourse(null);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateTextExercise_convertFromCourseToExamExercise_badRequest() throws Exception {
        Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);

        textExercise.setCourse(null);
        textExercise.setExerciseGroup(exerciseGroup);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateTextExercise_convertFromExamToCourseExercise_badRequest() throws Exception {
        Course course = database.addEmptyCourse();
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        textExercise.setExerciseGroup(null);
        textExercise.setCourse(course);

        request.putWithResponseBody("/api/text-exercises/", textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importTextExerciseFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course2);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importTextExerciseFromCourseToExam() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);
        textExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "TA")
    public void importTextExerciseFromCourseToExam_forbidden() throws Exception {
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importTextExerciseFromExamToCourse() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        Course course1 = database.addEmptyCourse();
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course1);
        textExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "TA")
    public void importTextExerciseFromExamToCourse_forbidden() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        Course course1 = database.addEmptyCourse();
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(course1);
        textExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importTextExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        ExerciseGroup exerciseGroup2 = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        textExerciseRepository.save(textExercise);
        textExercise.setExerciseGroup(exerciseGroup2);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importTextExerciseFromCourseToCourse_badRequest() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course1);
        textExerciseRepository.save(textExercise);
        textExercise.setCourse(null);

        request.postWithResponseBody("/api/text-exercises/import/" + textExercise.getId(), textExercise, TextExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllTextExercisesForCourse() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();

        List<TextExercise> textExercises = request.getList("/api/courses/" + course.getId() + "/text-exercises/", HttpStatus.OK, TextExercise.class);

        assertThat(textExercises.size()).as("text exercises for course were retrieved").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getTextExerciseAsTutor() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);

        TextExercise textExerciseServer = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);

        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getExamTextExerciseAsTutor_forbidden() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, TextExercise.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void getExamTextExerciseAsInstructor() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        textExerciseRepository.save(textExercise);

        TextExercise textExerciseServer = request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.OK, TextExercise.class);
        assertThat(textExerciseServer).as("text exercise was retrieved").isNotNull();
        assertThat(textExercise.getId()).as("Text exercise with the right id was retrieved").isEqualTo(textExercise.getId());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getTextExerciseAsStudent() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        request.get("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, TextExercise.class);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testTriggerAutomaticAssessment() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        request.postWithoutLocation("/api/text-exercises/" + textExercise.getId() + "/trigger-automatic-assessment", null, HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(value = "instructorother1", roles = "INSTRUCTOR")
    public void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        final var search = database.configureSearch("");
        final var result = request.get("/api/text-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testInstructorGetResultsFromOwningCoursesNotEmpty() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        database.addCourseWithOneReleasedTextExercise("Essay Bachelor");
        database.addCourseWithOneReleasedTextExercise("Essay Master");

        final var searchText = database.configureSearch("Text");
        final var resultText = request.get("/api/text-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(searchText));
        assertThat(resultText.getResultsOnPage().size()).isEqualTo(1);

        final var searchEssay = database.configureSearch("Essay");
        final var resultEssay = request.get("/api/text-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(searchEssay));
        assertThat(resultEssay.getResultsOnPage().size()).isEqualTo(2);

        final var searchNon = database.configureSearch("Non");
        final var resultNon = request.get("/api/text-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(searchNon));
        assertThat(resultNon.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testAdminGetsResultsFromAllCourses() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        database.addCourseInOtherInstructionGroupAndExercise("Text");

        final var search = database.configureSearch("Text");
        final var result = request.get("/api/text-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(search));
        assertThat(result.getResultsOnPage().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testImportTextExercise_team_modeChange() throws Exception {
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testImportTextExercise_individual_modeChange() throws Exception {
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCheckPlagiarismIdenticalLongTexts() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);

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

        // Use default options for plagiarism detection
        var params = new LinkedMultiValueMap<String, String>();
        params.add("similarityThreshold", "50");
        params.add("minimumScore", "0");
        params.add("minimumSize", "0");

        TextPlagiarismResult result = request.get("/api/text-exercises/" + textExercise.getId() + "/check-plagiarism", HttpStatus.OK, TextPlagiarismResult.class, params);
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
        assertThat(comparison.getMatches().size()).isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCheckPlagiarismIdenticalShortTexts() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);

        var shortText = "Lorem Ipsum Foo Bar";
        database.createSubmissionForTextExercise(textExercise, database.getUserByLogin("student1"), shortText);
        database.createSubmissionForTextExercise(textExercise, database.getUserByLogin("student2"), shortText);

        // Use default options for plagiarism detection
        var params = new LinkedMultiValueMap<String, String>();
        params.add("similarityThreshold", "50");
        params.add("minimumScore", "0");
        params.add("minimumSize", "5");

        TextPlagiarismResult result = request.get("/api/text-exercises/" + textExercise.getId() + "/check-plagiarism", HttpStatus.OK, TextPlagiarismResult.class, params);
        assertThat(result.getComparisons()).hasSize(0);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCheckPlagiarismNoSubmissions() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);

        // Use default options for plagiarism detection
        var params = new LinkedMultiValueMap<String, String>();
        params.add("similarityThreshold", "50");
        params.add("minimumScore", "0");
        params.add("minimumSize", "0");

        TextPlagiarismResult result = request.get("/api/text-exercises/" + textExercise.getId() + "/check-plagiarism", HttpStatus.OK, TextPlagiarismResult.class, params);
        assertThat(result.getComparisons()).hasSize(0);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetPlagiarismResult() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);

        TextPlagiarismResult expectedResult = database.createTextPlagiarismResultForExercise(textExercise);

        TextPlagiarismResult result = request.get("/api/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.OK, TextPlagiarismResult.class);
        assertThat(result.getId()).isEqualTo(expectedResult.getId());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetPlagiarismResultWithoutResult() throws Exception {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);

        TextPlagiarismResult result = request.get("/api/text-exercises/" + textExercise.getId() + "/plagiarism-result", HttpStatus.NOT_FOUND, TextPlagiarismResult.class);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetPlagiarismResultWithoutExercise() throws Exception {
        TextPlagiarismResult result = request.get("/api/text-exercises/" + 1 + "/plagiarism-result", HttpStatus.NOT_FOUND, TextPlagiarismResult.class);
        assertThat(result).isNull();
    }
}
