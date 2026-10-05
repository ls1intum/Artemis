package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.HashSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class CourseConfigurationDefaultsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Test
    void aFailingSettingsInsertIsReportedToTheCaller() throws SQLException {
        var course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), new HashSet<>());
        course.setShortName("failingdefaultstest");

        // A constraint scoped to this test's prefix forces a real database failure of one of the settings inserts. There is no
        // transaction around the statements, so the course is not rolled back; the caller gets the error and decides.
        setConfigurationInsertFailure(true);
        try {
            assertThatThrownBy(() -> courseRepository.saveWithDefaultConfigurations(course)).isInstanceOf(DataIntegrityViolationException.class);
        }
        finally {
            setConfigurationInsertFailure(false);
        }
    }

    @Test
    void courseCreationPersistsAllSettingsBeforeFeaturesAreConfigured() {
        var course = courseUtilService.createCourse();
        var jdbc = new JdbcTemplate(dataSource);
        assertThat(course.isOnlineCourse()).isFalse();
        for (String table : new String[] { "online_course_configuration", "tutorial_groups_configuration", "course_iris_settings" }) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE course_id = ?", Long.class, course.getId())).as(table).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE id = ? AND athena_config_id IS NOT NULL AND course_configuration_id IS NOT NULL", Long.class,
                course.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tutorial_groups_configuration WHERE course_id = ? AND tutorial_period_start_inclusive IS NULL AND tutorial_period_end_inclusive IS NULL",
                Long.class, course.getId())).isEqualTo(1);

        var originalSettings = jdbc.queryForList("SELECT id FROM online_course_configuration WHERE course_id = ?", course.getId());
        course.setOnlineCourse(true);
        courseRepository.saveWithDefaultConfigurations(course);
        course.setOnlineCourse(false);
        courseRepository.saveWithDefaultConfigurations(course);
        assertThat(jdbc.queryForList("SELECT id FROM online_course_configuration WHERE course_id = ?", course.getId())).isEqualTo(originalSettings);
    }

    @Test
    void courseWithoutShortNameGetsAnLtiPrefixDerivedFromItsId() {
        // short_name is nullable in the schema, so legacy courses can lack one; the default prefix then falls back to the id
        var course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), new HashSet<>());
        course.setShortName(null);
        var saved = courseRepository.saveWithDefaultConfigurations(course);

        var jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT user_prefix FROM online_course_configuration WHERE course_id = ?", String.class, saved.getId())).isEqualTo("course" + saved.getId());
    }

    @Test
    void exerciseSettingsSurviveTeamModeChangesAndRetainTheirIdsOnUpdate() {
        var course = courseUtilService.createCourse();
        var exercise = textExerciseUtilService.createSampleTextExercise(course);
        var jdbc = new JdbcTemplate(dataSource);
        var ids = jdbc.queryForMap("SELECT team_assignment_config_id, plagiarism_detection_config_id FROM exercise WHERE id = ?", exercise.getId());
        assertThat(ids.values()).doesNotContainNull();
        assertThat(exercise.getTeamAssignmentConfig()).isNull();

        var loaded = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(exercise.getId()).orElseThrow();
        loaded.setMode(ExerciseMode.TEAM);
        var teamSettings = new TeamAssignmentConfig();
        teamSettings.setMinTeamSize(2);
        teamSettings.setMaxTeamSize(5);
        loaded.setTeamAssignmentConfig(teamSettings);
        var plagiarismSettings = PlagiarismDetectionConfig.createDefault();
        plagiarismSettings.setSimilarityThreshold(77);
        loaded.setPlagiarismDetectionConfig(plagiarismSettings);
        textExerciseRepository.save(loaded);
        assertThat(jdbc.queryForMap("SELECT team_assignment_config_id, plagiarism_detection_config_id FROM exercise WHERE id = ?", exercise.getId())).isEqualTo(ids);

        loaded.setMode(ExerciseMode.INDIVIDUAL);
        loaded.setTeamAssignmentConfig(null);
        loaded.setPlagiarismDetectionConfig(null);
        textExerciseRepository.save(loaded);
        loaded = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(exercise.getId()).orElseThrow();
        assertThat(loaded.getTeamAssignmentConfig()).isNull();
        loaded.setMode(ExerciseMode.TEAM);
        assertThat(loaded.getTeamAssignmentConfig().getMinTeamSize()).isEqualTo(2);
        assertThat(loaded.getTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(5);
        assertThat(loaded.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(77);
        assertThat(jdbc.queryForMap("SELECT team_assignment_config_id, plagiarism_detection_config_id FROM exercise WHERE id = ?", exercise.getId())).isEqualTo(ids);
    }

    @Test
    void anExerciseBuiltFromALoadedOneGetsItsOwnSettingsRowsNotTheSources() {
        var course = courseUtilService.createCourse();
        var source = textExerciseUtilService.createSampleTextExercise(course);
        var jdbc = new JdbcTemplate(dataSource);
        var sourceIds = jdbc.queryForMap("SELECT team_assignment_config_id, plagiarism_detection_config_id FROM exercise WHERE id = ?", source.getId());

        // What an import does: a new exercise that still carries the source's stored (detached, id-bearing) settings
        var loadedSource = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(source.getId()).orElseThrow();
        var copy = new TextExercise();
        copy.setCourse(course);
        copy.setTitle("Imported");
        copy.setShortName("Imported");
        copy.setMaxPoints(10.0);
        copy.setBonusPoints(0.0);
        copy.setMode(ExerciseMode.TEAM);
        copy.setTeamAssignmentConfig(loadedSource.getStoredTeamAssignmentConfig());
        copy.setPlagiarismDetectionConfig(loadedSource.getPlagiarismDetectionConfig());
        assertThat(copy.getStoredTeamAssignmentConfig().getId()).isEqualTo(sourceIds.get("team_assignment_config_id"));

        var saved = textExerciseRepository.save(copy);

        var copyIds = jdbc.queryForMap("SELECT team_assignment_config_id, plagiarism_detection_config_id FROM exercise WHERE id = ?", saved.getId());
        assertThat(copyIds.values()).doesNotContainNull();
        assertThat(copyIds.get("team_assignment_config_id")).isNotEqualTo(sourceIds.get("team_assignment_config_id"));
        assertThat(copyIds.get("plagiarism_detection_config_id")).isNotEqualTo(sourceIds.get("plagiarism_detection_config_id"));
        assertThat(jdbc.queryForMap("SELECT team_assignment_config_id, plagiarism_detection_config_id FROM exercise WHERE id = ?", source.getId())).isEqualTo(sourceIds);
    }

    private void setConfigurationInsertFailure(boolean enabled) throws SQLException {
        // The test pool disables auto-commit. Commit the test-only DDL before exercising the repository transaction.
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            if (enabled) {
                statement.execute("ALTER TABLE online_course_configuration ADD CONSTRAINT fail_default_config_insert CHECK (user_prefix <> 'failingdefaultstest')");
            }
            else {
                String constraintType = connection.getMetaData().getDatabaseProductName().contains("MySQL") ? "CHECK" : "CONSTRAINT";
                statement.execute("ALTER TABLE online_course_configuration DROP " + constraintType + " fail_default_config_insert");
            }
        }
    }

}
