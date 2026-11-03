package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportBasicService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

/**
 * The permanent Metis configuration on a server that has the module switched off, which is how this context runs. None of the
 * module's repositories exists, and every programming exercise owns its row all the same, with the defaults: the exercise module
 * writes it with statements that name no Metis class. The rows are read with plain SQL, since nothing of the module can read them
 * here.
 * <p>
 * Tests that create or import an exercise through the application check the row as well, through
 * {@link ExerciseUtilService#assertHasPermanentConfigurations(long)}: with the module switched off, the exam import of
 * {@code ProgrammingExamIntegrationTest} in this context and the import from a file in the batch Jenkins context. This one checks
 * the absence of the module and the values of the row.
 */
class MetisModuleDisabledIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "metisdisabled";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    /** Reads the configuration rows, which no repository of this context can. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** For the one write that has to stay: the pool of the tests disables auto-commit, so it runs on a connection of its own. */
    @Autowired
    private DataSource dataSource;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        // Stored the way every creation path stores a programming exercise: saved, then ExerciseConfigurationService.initialize, the call the creation service makes.
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        exercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class));
    }

    @Test
    void theModuleIsSwitchedOffAndNoneOfItsRepositoriesExists() {
        assertThat(environment.getProperty(Constants.METIS_ENABLED_PROPERTY_NAME, Boolean.class)).isFalse();
        for (Class<?> repository : List.of(MetisExerciseConfigRepository.class, MetisGroupWindowRepository.class, MetisCheckRepository.class, MetisQuestionRepository.class)) {
            assertThat(applicationContext.getBeanNamesForType(repository)).as("beans of %s", repository.getSimpleName()).isEmpty();
        }
    }

    @Test
    void aStoredProgrammingExerciseOwnsItsConfigurationWithTheDefaults() {
        assertThat(storedConfiguration(exercise.getId())).usingRecursiveComparison().isEqualTo(defaultsOf(exercise.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void anImportedProgrammingExerciseGetsAConfigurationOfItsOwnWithTheDefaults() throws SQLException {
        // Settings an instructor chose for the source, written directly, since no repository of the module exists here.
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("UPDATE metis_exercise_config SET enabled = ?, main_question_count = ?, review_required = ? WHERE exercise_id = ?")) {
            connection.setAutoCommit(true);
            statement.setBoolean(1, true);
            statement.setInt(2, 5);
            statement.setBoolean(3, true);
            statement.setLong(4, exercise.getId());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        Course targetCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        ProgrammingExercise toBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("Imported", "IMPORTED", exercise, targetCourse);

        ProgrammingExercise imported = programmingExerciseImportBasicService.importProgrammingExerciseBasis(exercise, programmingExerciseUtilService.buildConfigOf(exercise),
                toBeImported, null);

        assertThat(imported.getId()).isNotEqualTo(exercise.getId());
        assertThat(storedConfiguration(imported.getId())).usingRecursiveComparison().isEqualTo(defaultsOf(imported.getId()));
        assertThat(storedConfiguration(exercise.getId())).as("the source keeps its settings")
                .extracting(MetisExerciseConfig::isEnabled, MetisExerciseConfig::getMainQuestionCount, MetisExerciseConfig::isReviewRequired).containsExactly(true, 5, true);
    }

    private static MetisExerciseConfig defaultsOf(long exerciseId) {
        MetisExerciseConfig defaults = new MetisExerciseConfig();
        defaults.setExerciseId(exerciseId);
        return defaults;
    }

    /**
     * Reads the configuration row of an exercise with plain SQL, column by column, onto an entity, so that it compares with the defaults of the entity.
     *
     * @param exerciseId the exercise
     * @return the stored configuration
     */
    private MetisExerciseConfig storedConfiguration(long exerciseId) {
        return jdbcTemplate.queryForObject("""
                SELECT exercise_id, enabled, window_start, window_end, main_question_count, max_follow_ups_per_question, min_seconds_per_question,
                    max_seconds_per_question, answer_length, complexity, likert_scale_points, follow_up_threshold, review_required, instructor_hint
                FROM metis_exercise_config
                WHERE exercise_id = ?
                """, (row, rowNumber) -> {
            MetisExerciseConfig config = new MetisExerciseConfig();
            config.setExerciseId(row.getLong("exercise_id"));
            config.setEnabled(row.getBoolean("enabled"));
            config.setWindowStart(instantOf(row, "window_start"));
            config.setWindowEnd(instantOf(row, "window_end"));
            config.setMainQuestionCount(row.getInt("main_question_count"));
            config.setMaxFollowUpsPerQuestion(row.getInt("max_follow_ups_per_question"));
            config.setMinSecondsPerQuestion(row.getInt("min_seconds_per_question"));
            config.setMaxSecondsPerQuestion(row.getInt("max_seconds_per_question"));
            config.setAnswerLength(row.getInt("answer_length"));
            config.setComplexity(row.getInt("complexity"));
            config.setLikertScalePoints(row.getInt("likert_scale_points"));
            config.setFollowUpThreshold(row.getInt("follow_up_threshold"));
            config.setReviewRequired(row.getBoolean("review_required"));
            config.setInstructorHint(row.getString("instructor_hint"));
            return config;
        }, exerciseId);
    }

    @Nullable
    private static ZonedDateTime instantOf(ResultSet row, String column) throws SQLException {
        Timestamp timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant().atZone(ZoneOffset.UTC);
    }
}
