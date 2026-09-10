package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.dto.AthenaFeedbackSettingsDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Spring Data JPA repository for the {@link CourseAthenaConfig} entity. Used to read a course's Athena settings
 * independently of the (lazy) association on the course, the same way {@link CourseConfigurationRepository} reads the
 * course configuration.
 * <p>
 * The queries start from {@code Course} because the foreign key lives there ({@code course.athena_config_id}), so the
 * config has no owning reference back to the course to filter on.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseAthenaConfigRepository extends ArtemisJpaRepository<CourseAthenaConfig, Long> {

    /**
     * Finds the Athena configuration of the given course, if one exists.
     *
     * @param courseId the id of the course
     * @return the configuration, or empty when the course has none yet
     */
    @Query("""
            SELECT athenaConfig
            FROM Course course
                JOIN course.athenaConfig athenaConfig
            WHERE course.id = :courseId
            """)
    Optional<CourseAthenaConfig> findByCourseId(@Param("courseId") long courseId);

    /**
     * The two feedback switches of the course an exercise belongs to, resolved through either the course or the exam.
     * <p>
     * Reads the flags rather than the entity: this answers a yes/no on a request path, and an exercise belongs either to
     * a course or to an exercise group, never both. A course without a configuration row counts as switched off.
     *
     * @param exerciseId the id of the exercise
     * @return the settings, defaulting to off
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.course.dto.AthenaFeedbackSettingsDTO(
                COALESCE(courseConfig.gradingFeedbackEnabled, examCourseConfig.gradingFeedbackEnabled, FALSE),
                COALESCE(courseConfig.formativeFeedbackEnabled, examCourseConfig.formativeFeedbackEnabled, FALSE))
            FROM Exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN course.athenaConfig courseConfig
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
                LEFT JOIN examCourse.athenaConfig examCourseConfig
            WHERE exercise.id = :exerciseId
            """)
    Optional<AthenaFeedbackSettingsDTO> findFeedbackSettingsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * The two feedback switches of a course.
     *
     * @param courseId the id of the course
     * @return the settings, defaulting to off
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.course.dto.AthenaFeedbackSettingsDTO(
                COALESCE(athenaConfig.gradingFeedbackEnabled, FALSE), COALESCE(athenaConfig.formativeFeedbackEnabled, FALSE))
            FROM Course course
                LEFT JOIN course.athenaConfig athenaConfig
            WHERE course.id = :courseId
            """)
    Optional<AthenaFeedbackSettingsDTO> findFeedbackSettingsByCourseId(@Param("courseId") long courseId);

    /**
     * Puts the course's Athena configuration onto the course of the given exercise, so that the code downstream of an
     * entry point can keep asking {@code Exercise#areFeedbackSuggestionsEnabled()} without every one of those layers
     * needing a repository of its own.
     * <p>
     * Call this at the point where an Athena flow starts. It is a no-op for an exercise without a course, and leaves
     * the configuration null when the course has none - which reads as switched off, the right answer.
     *
     * @param exercise the exercise whose course should carry its Athena configuration
     */
    default void attachToCourseOf(Exercise exercise) {
        attachTo(exercise == null ? null : exercise.getCourseViaExerciseGroupOrCourseMember());
    }

    /**
     * Puts a course's Athena configuration onto it, for a response that reports the two switches.
     *
     * @param course the course, may be null
     */
    default void attachTo(Course course) {
        if (course != null) {
            course.setAthenaConfig(findByCourseId(course.getId()).orElse(null));
        }
    }
}
