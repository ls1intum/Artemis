package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * @return the course configuration or an empty optional if the course has none yet
     */
    @Query("""
            SELECT configuration
            FROM CourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    Optional<CourseConfiguration> findByCourseId(@Param("courseId") long courseId);

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
