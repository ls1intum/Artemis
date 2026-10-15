package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * The statements that give a course its permanent default settings rows and repair a course that is missing some. They are
 * part of {@link CourseRepository}, which inherits them; they live here only to keep that repository at a manageable size.
 * <p>
 * Each statement is a plain, single read or write: no transaction spans them and no lock is held across them.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
@NoRepositoryBean
public interface CourseDefaultConfigurationsRepository {

    /**
     * Applies the settings the creating request carried on the course's slots to the permanent rows that were just created
     * for it. A slot that is empty leaves the defaults in place.
     *
     * @param course   the course that was just stored, carrying the requested settings on its slots
     * @param courseId the id of the stored course
     */
    default void applyRequestedConfigurations(Course course, long courseId) {
        var athenaConfig = course.getAthenaConfig();
        if (athenaConfig != null) {
            updateAthenaConfig(courseId, athenaConfig.isGradingFeedbackEnabled(), athenaConfig.isFormativeFeedbackEnabled());
        }
        var courseConfiguration = course.getCourseConfiguration();
        if (courseConfiguration != null) {
            updateCourseConfiguration(courseId, courseConfiguration.isGradeRelevant(), courseConfiguration.isDataRetentionHold(), courseConfiguration.isAutoOrchestratorEnabled(),
                    courseConfiguration.getDebounceWindowSecondsOverride(), courseConfiguration.getMaxDailyOrchestrationOverride());
        }
    }

    /**
     * Changes the Athena settings of a course in place.
     *
     * @param courseId                 the id of the course
     * @param gradingFeedbackEnabled   whether Athena grading feedback is on
     * @param formativeFeedbackEnabled whether Athena formative feedback is on
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseAthenaConfig config
            SET config.gradingFeedbackEnabled = :gradingFeedbackEnabled, config.formativeFeedbackEnabled = :formativeFeedbackEnabled
            WHERE config.course.id = :courseId
            """)
    void updateAthenaConfig(@Param("courseId") long courseId, @Param("gradingFeedbackEnabled") boolean gradingFeedbackEnabled,
            @Param("formativeFeedbackEnabled") boolean formativeFeedbackEnabled);

    /**
     * Changes the general settings of a course in place.
     *
     * @param courseId                      the id of the course
     * @param gradeRelevant                 whether the course is grade-relevant
     * @param dataRetentionHold             whether the course is under a data-retention hold
     * @param autoOrchestratorEnabled       whether the auto-orchestration pipeline is on
     * @param debounceWindowSecondsOverride the debounce override, or null for the global default
     * @param maxDailyOrchestrationOverride the daily run cap override, or null for the global default
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseConfiguration config
            SET config.gradeRelevant = :gradeRelevant, config.dataRetentionHold = :dataRetentionHold, config.autoOrchestratorEnabled = :autoOrchestratorEnabled,
                config.debounceWindowSecondsOverride = :debounceWindowSecondsOverride, config.maxDailyOrchestrationOverride = :maxDailyOrchestrationOverride
            WHERE config.course.id = :courseId
            """)
    void updateCourseConfiguration(@Param("courseId") long courseId, @Param("gradeRelevant") boolean gradeRelevant, @Param("dataRetentionHold") boolean dataRetentionHold,
            @Param("autoOrchestratorEnabled") boolean autoOrchestratorEnabled, @Param("debounceWindowSecondsOverride") @Nullable Integer debounceWindowSecondsOverride,
            @Param("maxDailyOrchestrationOverride") @Nullable Integer maxDailyOrchestrationOverride);

    /**
     * Creates the Athena settings of a newly stored course with every feature switched off.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO course_athena_config (course_id, grading_feedback_enabled, formative_feedback_enabled)
            VALUES (:courseId, FALSE, FALSE)
            """, nativeQuery = true)
    void initializeAthenaConfig(@Param("courseId") long courseId);

    /**
     * Creates the general course settings of a newly stored course: grade-relevant, no retention hold, no auto-orchestration.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO course_configuration (course_id, grade_relevant, data_retention_hold, auto_orchestrator_enabled)
            VALUES (:courseId, TRUE, FALSE, FALSE)
            """, nativeQuery = true)
    void initializeCourseConfiguration(@Param("courseId") long courseId);

    /**
     * Inserts the default settings a stored course is missing, which is what a creation that failed half-way leaves behind.
     * For a course that has all its settings this is five unlocked reads and nothing else.
     * <p>
     * It takes no lock and needs no transaction: a missing row is found by a plain read and added by a plain insert. If two
     * saves repair the same course at the same moment, the unique key lets one insert win and the other finds the row
     * already there. Settings of a course are never removed or replaced here, only added where they are missing.
     *
     * @param courseId the id of the stored course
     * @return the number of settings rows that had to be added; greater than zero means an earlier creation was incomplete
     */
    default int ensureDefaultConfigurations(long courseId) {
        int added = 0;
        if (!hasAthenaConfig(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeAthenaConfig(courseId), () -> hasAthenaConfig(courseId));
        }
        if (!hasCourseConfiguration(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeCourseConfiguration(courseId), () -> hasCourseConfiguration(courseId));
        }
        if (!hasOnlineCourseConfiguration(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeOnlineCourseConfiguration(courseId), () -> hasOnlineCourseConfiguration(courseId));
        }
        if (!hasTutorialGroupsConfiguration(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeTutorialGroupsConfiguration(courseId), () -> hasTutorialGroupsConfiguration(courseId));
        }
        if (!hasIrisCourseSettings(courseId)) {
            added += insertUnlessAlreadyThere(() -> initializeIrisCourseSettings(courseId), () -> hasIrisCourseSettings(courseId));
        }
        return added;
    }

    private static int insertUnlessAlreadyThere(Runnable insert, BooleanSupplier exists) {
        try {
            insert.run();
            return 1;
        }
        catch (DataIntegrityViolationException e) {
            // a concurrent save adding the same row first is what was wanted; any other violation is a real failure
            if (exists.getAsBoolean()) {
                return 0;
            }
            throw e;
        }
    }

    @Query("""
            SELECT COUNT(config) > 0
            FROM CourseAthenaConfig config
            WHERE config.course.id = :courseId
            """)
    boolean hasAthenaConfig(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(config) > 0
            FROM CourseConfiguration config
            WHERE config.course.id = :courseId
            """)
    boolean hasCourseConfiguration(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(configuration) > 0
            FROM OnlineCourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    boolean hasOnlineCourseConfiguration(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(configuration) > 0
            FROM TutorialGroupsConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    boolean hasTutorialGroupsConfiguration(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(settings) > 0
            FROM IrisCourseSettingsEntity settings
            WHERE settings.courseId = :courseId
            """)
    boolean hasIrisCourseSettings(@Param("courseId") long courseId);

    /**
     * Creates the default LTI settings for a newly stored course, independently of whether online mode is enabled.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO online_course_configuration (course_id, user_prefix, require_existing_user)
            SELECT id, COALESCE(short_name, CONCAT('course', id)), FALSE FROM course WHERE id = :courseId
            """, nativeQuery = true)
    void initializeOnlineCourseConfiguration(@Param("courseId") long courseId);

    /**
     * Creates inactive tutorial-group settings; a tutorial period must be configured before the feature becomes available.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO tutorial_groups_configuration (course_id, use_tutorial_group_channels, use_public_tutorial_group_channels)
            VALUES (:courseId, FALSE, FALSE)
            """, nativeQuery = true)
    void initializeTutorialGroupsConfiguration(@Param("courseId") long courseId);

    /**
     * Creates Iris settings matching the defaults previously used when a course had no settings row.
     *
     * @param courseId the new course id
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO course_iris_settings (course_id, settings)
            VALUES (:courseId, '{"enabled":true,"variant":"default","supportLevel":"moderate"}')
            """, nativeQuery = true)
    void initializeIrisCourseSettings(@Param("courseId") long courseId);
}
