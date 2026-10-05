package de.tum.cit.aet.artemis.exercise;

import static de.tum.cit.aet.artemis.core.util.QueryCountAssert.assertThatDb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseResponseDTO;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Pins what reading an exercise costs.
 * <p>
 * The team assignment configuration holds the key to its exercise, and the exercise carries no mapped association to it,
 * so loading an exercise - of any type, on its own, through its course or through its exam - reads the exercise rows and
 * nothing else. Before the key moved, about thirty entity graphs and fetch joins pulled the table in for every exercise
 * they returned, whether or not the caller looked at it. The tests below therefore look at the statements themselves
 * rather than at their number: no read of an exercise may mention the table, and the flows that do report the
 * configuration read it exactly once, however many exercises they return.
 */
class ExerciseConfigurationLoadProfileTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseloadprofile";

    // a word boundary, because the name is a prefix of nothing else, but the pattern should say it does not matter
    private static final Pattern TEAM_ASSIGNMENT_CONFIG_TABLE = Pattern.compile("\\bteam_assignment_config\\b");

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TeamAssignmentConfigRepository teamAssignmentConfigRepository;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ExerciseConfigurationService exerciseConfigurationService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private long courseId;

    private long examId;

    private long textExerciseId;

    private long modelingExerciseId;

    private long fileUploadExerciseId;

    private long programmingExerciseId;

    private long examExerciseId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        courseId = course.getId();

        textExerciseId = withTeamConfig(textExerciseUtilService.createSampleTextExercise(course)).getId();
        modelingExerciseId = withTeamConfig(modelingExerciseUtilService.addModelingExerciseToCourse(course)).getId();
        fileUploadExerciseId = withTeamConfig(fileUploadExerciseUtilService.addFileUploadExercise(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(2),
                ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2))).getId();
        programmingExerciseId = withTeamConfig(programmingExerciseUtilService.addProgrammingExerciseToCourse(course)).getId();

        Exam exam = examUtilService.addExamWithExerciseGroup(courseRepository.findByIdElseThrow(courseId), true);
        examId = exam.getId();
        examExerciseId = exerciseUtilService.initializeConfigurations(exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().getFirst())))
                .getId();
    }

    private <E extends Exercise> E withTeamConfig(E exercise) {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setMinTeamSize(2);
        config.setMaxTeamSize(4);
        // the settings are reported for team exercises only
        exercise.setMode(ExerciseMode.TEAM);
        exercise = exerciseRepository.save(exercise);
        return exerciseUtilService.saveTeamAssignmentConfig(exercise, config);
    }

    @Test
    void readingAnExerciseOfAnyTypeDoesNotReadItsTeamAssignmentConfig() throws Exception {
        for (long id : List.of(textExerciseId, modelingExerciseId, fileUploadExerciseId, programmingExerciseId)) {
            assertThat(readsTheTable(() -> exerciseRepository.findByIdElseThrow(id))).as("reading exercise " + id).isFalse();
        }
    }

    @Test
    void readingTheExercisesOfACourseDoesNotReadTheTeamAssignmentConfig() throws Exception {
        assertThat(readsTheTable(() -> courseRepository.findWithEagerExercisesById(courseId))).isFalse();
    }

    @Test
    void readingTheExercisesOfAnExamDoesNotReadTheTeamAssignmentConfig() throws Exception {
        assertThat(readsTheTable(() -> examRepository.findWithExerciseGroupsAndExercisesById(examId))).isFalse();
        assertThat(readsTheTable(() -> exerciseRepository.findByIdElseThrow(examExerciseId))).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void readingAnExerciseThroughTheApiReadsTheConfigurationExactlyOnce() throws Exception {
        List<String> statements = statementsOf(() -> request.get("/api/exercise/exercises/" + textExerciseId, HttpStatus.OK, ExerciseResponseDTO.class));

        assertThat(statements.stream().filter(sql -> TEAM_ASSIGNMENT_CONFIG_TABLE.matcher(sql).find())).as("reads of team_assignment_config").hasSize(1);

        // and what it read reaches the response, which is what the client shows
        ExerciseResponseDTO exercise = request.get("/api/exercise/exercises/" + textExerciseId, HttpStatus.OK, ExerciseResponseDTO.class);
        assertThat(exercise.teamAssignmentConfig()).isNotNull();
        assertThat(exercise.teamAssignmentConfig().minTeamSize()).isEqualTo(2);
        assertThat(exercise.teamAssignmentConfig().maxTeamSize()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void listingTheExercisesOfACourseReadsTheConfigurationsInOneQuery() throws Exception {
        String url = "/api/course/courses/" + courseId + "/with-exercises";
        List<String> statements = statementsOf(() -> request.get(url, HttpStatus.OK, String.class));

        assertThat(statements.stream().filter(sql -> TEAM_ASSIGNMENT_CONFIG_TABLE.matcher(sql).find())).as("reads of team_assignment_config for four exercises").hasSize(1);

        JsonNode exercises = JsonObjectMapper.get().readTree(request.get(url, HttpStatus.OK, String.class)).get("exercises");
        assertThat(exercises).hasSize(4);
        // the lean file upload list entry has never reported it, the other three types do
        exercises.forEach(exercise -> {
            if (!"file-upload".equals(exercise.get("type").asString())) {
                assertThat(exercise.get("teamAssignmentConfig")).as("team configuration of exercise " + exercise.get("id")).isNotNull();
            }
        });
    }

    @Test
    void attachingTheConfigurationsOfSeveralExercisesCostsOneQuery() throws Exception {
        List<Exercise> exercises = List.of(exerciseRepository.findByIdElseThrow(textExerciseId), exerciseRepository.findByIdElseThrow(modelingExerciseId),
                exerciseRepository.findByIdElseThrow(fileUploadExerciseId), exerciseRepository.findByIdElseThrow(programmingExerciseId));

        assertThatDb(() -> {
            teamAssignmentConfigRepository.attachTo(exercises);
            return exercises;
        }).hasBeenCalledTimes(1);

        assertThat(exercises).allSatisfy(exercise -> assertThat(exercise.getStoredTeamAssignmentConfig()).isNotNull());
    }

    @Test
    void deletingTheExerciseDeletesItsConfiguration() {
        long configId = teamAssignmentConfigRepository.findByExerciseId(textExerciseId).orElseThrow().getId();

        // the configuration holds the key to its exercise, so the database removes it with the exercise
        exerciseRepository.deleteById(textExerciseId);

        assertThat(teamAssignmentConfigRepository.findById(configId)).isEmpty();
        assertThat(teamAssignmentConfigRepository.findByExerciseId(textExerciseId)).isEmpty();
    }

    @Test
    void updatingTheSettingsKeepsTheSameRow() {
        Exercise exercise = exerciseRepository.findByIdElseThrow(textExerciseId);
        long idBefore = teamAssignmentConfigRepository.findByExerciseId(textExerciseId).orElseThrow().getId();

        TeamAssignmentConfig replacement = new TeamAssignmentConfig();
        replacement.setMinTeamSize(3);
        replacement.setMaxTeamSize(6);
        teamAssignmentConfigRepository.applyTo(exercise, replacement);
        teamAssignmentConfigRepository.applyTo(exercise, replacement);

        List<TeamAssignmentConfig> rows = teamAssignmentConfigRepository.findAllByExerciseIdIn(List.of(textExerciseId));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getId()).as("the permanent row is updated in place, never replaced").isEqualTo(idBefore);
        assertThat(rows.getFirst().getMinTeamSize()).isEqualTo(3);
        assertThat(rows.getFirst().getMaxTeamSize()).isEqualTo(6);
        assertThat(exercise.getStoredTeamAssignmentConfig().getId()).isEqualTo(idBefore);
    }

    @Test
    void requestingNoSettingsKeepsTheStoredOnes() {
        Exercise exercise = exerciseRepository.findByIdElseThrow(textExerciseId);

        TeamAssignmentConfig reported = teamAssignmentConfigRepository.applyTo(exercise, null);

        assertThat(reported.getMinTeamSize()).isEqualTo(2);
        assertThat(reported.getMaxTeamSize()).isEqualTo(4);
    }

    @Test
    void insertingTheDefaultsAgainChangesNothing() {
        long idBefore = teamAssignmentConfigRepository.findByExerciseId(textExerciseId).orElseThrow().getId();

        exerciseConfigurationService.initialize(exerciseRepository.findByIdElseThrow(textExerciseId));
        exerciseConfigurationService.initialize(exerciseRepository.findByIdElseThrow(textExerciseId));

        List<TeamAssignmentConfig> rows = teamAssignmentConfigRepository.findAllByExerciseIdIn(List.of(textExerciseId));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getId()).isEqualTo(idBefore);
        assertThat(rows.getFirst().getMinTeamSize()).as("the stored settings are not reset by the idempotent insert").isEqualTo(2);
    }

    @Test
    void anExerciseStoredWithoutItsRowIsReportedInsteadOfHealedBehindTheCallersBack() {
        Exercise exercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(examExercise().getExerciseGroup()));

        assertThatThrownBy(() -> teamAssignmentConfigRepository.applyTo(exercise, null)).isInstanceOf(EntityNotFoundException.class);
        assertThat(teamAssignmentConfigRepository.findByExerciseId(exercise.getId())).isEmpty();
    }

    @Test
    void anExerciseOfEveryTypeGetsDefaultsWhenNoSettingsAreRequested() {
        Exercise exercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(examExercise().getExerciseGroup()));

        exerciseConfigurationService.initialize(exercise);

        TeamAssignmentConfig stored = teamAssignmentConfigRepository.findByExerciseId(exercise.getId()).orElseThrow();
        assertThat(stored.getMinTeamSize()).isEqualTo(1);
        assertThat(stored.getMaxTeamSize()).isEqualTo(1);
    }

    private Exercise examExercise() {
        return exerciseRepository.findByIdElseThrow(examExerciseId);
    }

    @Test
    void aConfigurationWithoutAnExerciseCannotBeWritten() {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setMinTeamSize(1);
        assertThatThrownBy(() -> teamAssignmentConfigRepository.saveAndFlush(config)).as("a team assignment configuration without an exercise").isNotNull();
    }

    @Test
    void anExerciseOfAnotherTypeHasItsOwnConfiguration() {
        // one row per exercise, matched through the key on the configuration
        assertThat(teamAssignmentConfigRepository.findAllByExerciseIdIn(List.of(textExerciseId, modelingExerciseId, fileUploadExerciseId, programmingExerciseId))).hasSize(4)
                .extracting(TeamAssignmentConfig::getExerciseId).containsExactlyInAnyOrder(textExerciseId, modelingExerciseId, fileUploadExerciseId, programmingExerciseId);
        assertThat(exerciseRepository.findByIdElseThrow(textExerciseId)).isInstanceOf(TextExercise.class);
    }

    private boolean readsTheTable(ThrowingCall call) throws Exception {
        return statementsOf(call).stream().anyMatch(sql -> TEAM_ASSIGNMENT_CONFIG_TABLE.matcher(sql).find());
    }

    private List<String> statementsOf(ThrowingCall call) throws Exception {
        queryInterceptor.startQueryCount();
        call.run();
        return queryInterceptor.getStatements();
    }

    @FunctionalInterface
    private interface ThrowingCall {

        void run() throws Exception;
    }
}
