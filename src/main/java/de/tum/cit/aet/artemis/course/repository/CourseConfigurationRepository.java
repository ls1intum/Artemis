package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;

/**
 * Spring Data JPA repository for the {@link CourseConfiguration} entity. The configuration holds the key to its course and
 * the course carries no mapped association to it, so loading a course never reads the configuration. A flow that needs
 * it reads it here, e.g. when updating the grade-relevance flag, and may attach it to the course it already holds with
 * {@link #attachTo(Course)} or {@link #attachTo(Collection)}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseConfigurationRepository extends ArtemisJpaRepository<CourseConfiguration, Long> {

    /**
     * Finds the configuration of the given course, if one exists.
     *
     * @param courseId the id of the course
     * @return the permanent course configuration or an empty optional for an unknown course
     */
    @Query("""
            SELECT configuration
            FROM CourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    Optional<CourseConfiguration> findByCourseId(@Param("courseId") long courseId);

    /**
     * Reads the presentation-assessment switch without loading the course or the full configuration entity.
     *
     * @param courseId the id of the course
     * @return the switch value, or empty when the course has no configuration
     */
    @Query("""
            SELECT configuration.presentationAssessmentsEnabled
            FROM CourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    Optional<Boolean> findPresentationAssessmentsEnabledByCourseId(@Param("courseId") long courseId);

    /**
     * Lightweight projection of a course's auto-orchestration configuration (kill switch plus the nullable debounce /
     * daily-cap overrides), read on the Atlas accumulator hot path without loading the full entity. Returns empty when the
     * course has no configuration row, in which case callers fall back to the global defaults and treat the pipeline as
     * disabled.
     *
     * @param courseId the course to resolve the configuration for
     * @return the projected configuration, or empty when the course has no configuration row
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO(
                configuration.autoOrchestratorEnabled, configuration.debounceWindowSecondsOverride, configuration.maxDailyOrchestrationOverride)
            FROM CourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    Optional<CourseAutoOrchestrationConfigDTO> findAutoOrchestrationConfigByCourseId(@Param("courseId") long courseId);

    /**
     * Changes the settings an instructor or administrator edits in the course update, in place. The retention bookkeeping
     * (warning and reset dates) and the retention hold are not touched, so a concurrent cleanup run or hold change survives.
     *
     * @param courseId                      the id of the course
     * @param gradeRelevant                 whether the course is grade-relevant
     * @param autoOrchestratorEnabled       whether the auto-orchestration pipeline is on
     * @param debounceWindowSecondsOverride the debounce override, or null for the global default
     * @param maxDailyOrchestrationOverride the daily run cap override, or null for the global default
     * @return the number of updated rows: 1, or 0 if the course has no configuration row
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseConfiguration configuration
            SET configuration.gradeRelevant = :gradeRelevant, configuration.autoOrchestratorEnabled = :autoOrchestratorEnabled,
                configuration.debounceWindowSecondsOverride = :debounceWindowSecondsOverride, configuration.maxDailyOrchestrationOverride = :maxDailyOrchestrationOverride
            WHERE configuration.course.id = :courseId
            """)
    int updateEditableSettings(@Param("courseId") long courseId, @Param("gradeRelevant") boolean gradeRelevant, @Param("autoOrchestratorEnabled") boolean autoOrchestratorEnabled,
            @Param("debounceWindowSecondsOverride") @Nullable Integer debounceWindowSecondsOverride,
            @Param("maxDailyOrchestrationOverride") @Nullable Integer maxDailyOrchestrationOverride);

    /**
     * Changes the data-retention hold of a course in place.
     *
     * @param courseId          the id of the course
     * @param dataRetentionHold whether the course is under a retention hold
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseConfiguration configuration
            SET configuration.dataRetentionHold = :dataRetentionHold
            WHERE configuration.course.id = :courseId
            """)
    int updateDataRetentionHold(@Param("courseId") long courseId, @Param("dataRetentionHold") boolean dataRetentionHold);

    /**
     * Records that the instructors of a course were warned about the upcoming student-data reset. Only a course that has
     * neither been warned nor reset and is not under a retention hold is marked, so a hold or a reset that happened while
     * the archive was written is not overwritten.
     *
     * @param courseId the id of the course
     * @param warnedAt when the warning was sent
     * @return the number of updated rows: 1 if the course was marked, 0 if it left the cleanup in the meantime
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseConfiguration configuration
            SET configuration.resetWarningSentDate = :warnedAt
            WHERE configuration.course.id = :courseId
                AND configuration.resetWarningSentDate IS NULL
                AND configuration.studentDataResetDate IS NULL
                AND configuration.dataRetentionHold = FALSE
            """)
    int markResetWarningSent(@Param("courseId") long courseId, @Param("warnedAt") ZonedDateTime warnedAt);

    /**
     * Records that the student data of a course was reset.
     *
     * @param courseId the id of the course
     * @param resetAt  when the reset happened
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseConfiguration configuration
            SET configuration.studentDataResetDate = :resetAt
            WHERE configuration.course.id = :courseId
                AND configuration.studentDataResetDate IS NULL
            """)
    int markStudentDataReset(@Param("courseId") long courseId, @Param("resetAt") ZonedDateTime resetAt);

    /**
     * Withdraws the reset warning of a course that has not been reset.
     *
     * @param courseId the id of the course
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseConfiguration configuration
            SET configuration.resetWarningSentDate = NULL
            WHERE configuration.course.id = :courseId
                AND configuration.studentDataResetDate IS NULL
            """)
    int clearResetWarning(@Param("courseId") long courseId);

    /**
     * Reads the configurations of several courses in one query.
     *
     * @param courseIds the ids of the courses
     * @return the configurations that exist; a course without one has no entry, and {@link CourseConfiguration#getCourseId()}
     *         says which course each one belongs to
     */
    @Query("""
            SELECT configuration
            FROM CourseConfiguration configuration
            WHERE configuration.course.id IN :courseIds
            """)
    List<CourseConfiguration> findAllByCourseIdIn(@Param("courseIds") Collection<Long> courseIds);

    /**
     * Puts a course's configuration onto it, for a flow that reads or changes it through the course. A course without a
     * configuration is left empty, which reads as the defaults.
     *
     * @param course the course, may be null
     */
    default void attachTo(Course course) {
        if (course != null) {
            course.setCourseConfiguration(findByCourseId(course.getId()).orElse(null));
        }
    }

    /**
     * Puts the configurations of several courses onto them with one query per {@value #ATTACH_BATCH_SIZE} courses, so
     * that a cleanup that looks at many courses does not read one configuration per course.
     *
     * @param courses the courses to attach the configurations to
     */
    default void attachTo(Collection<Course> courses) {
        Map<Long, Course> coursesById = new HashMap<>();
        courses.forEach(course -> coursesById.put(course.getId(), course));
        List<Long> courseIds = List.copyOf(coursesById.keySet());
        courses.forEach(course -> course.setCourseConfiguration(null));
        for (int from = 0; from < courseIds.size(); from += ATTACH_BATCH_SIZE) {
            List<Long> batch = courseIds.subList(from, Math.min(from + ATTACH_BATCH_SIZE, courseIds.size()));
            findAllByCourseIdIn(batch).forEach(configuration -> coursesById.get(configuration.getCourseId()).setCourseConfiguration(configuration));
        }
    }

    /** The number of courses whose configurations are read by one query when attaching them in bulk. */
    int ATTACH_BATCH_SIZE = 1000;
}
