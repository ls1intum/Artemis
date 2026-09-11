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
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Spring Data JPA repository for the {@link CourseAthenaConfig} entity, which reads a course's Athena settings without
 * the lazy association on the course, as {@link CourseConfigurationRepository} does for the course configuration. The
 * queries start from {@code Course} because the foreign key lives there.
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
     * Puts the configuration onto the course of the given exercise, so the code below an entry point can keep asking
     * {@code Exercise#areFeedbackSuggestionsEnabled()}. Call it where an Athena flow starts. A course without a
     * configuration stays null, which reads as switched off.
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
