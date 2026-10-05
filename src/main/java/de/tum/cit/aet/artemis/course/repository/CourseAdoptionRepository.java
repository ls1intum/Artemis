package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;

/**
 * Aggregate counts of how widely the optional features of a course are switched on, for the admin feature usage page.
 * <p>
 * Kept separate from {@link CourseRepository} because these queries have only one caller and share nothing with the rest.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseAdoptionRepository extends ArtemisJpaRepository<Course, Long> {

    /**
     * Counts the courses that have communication switched on in any form.
     * <p>
     * Phrased as "not disabled and not unknown" rather than as a total minus the disabled ones, because a course whose
     * configuration is null would otherwise be silently counted as having communication enabled.
     *
     * @param disabledConfiguration the value that means communication is off
     * @return how many courses have some form of communication enabled
     */
    @Query("""
            SELECT COUNT(course)
            FROM Course course
            WHERE course.courseInformationSharingConfiguration IS NOT NULL
                AND course.courseInformationSharingConfiguration <> :disabledConfiguration
            """)
    long countWithCommunication(@Param("disabledConfiguration") CourseInformationSharingConfiguration disabledConfiguration);

    @Query("""
            SELECT COUNT(course)
            FROM Course course
            WHERE course.learningPathsEnabled IS TRUE
            """)
    long countWithLearningPaths();

    @Query("""
            SELECT COUNT(course)
            FROM Course course
            WHERE course.enrollmentEnabled IS TRUE
            """)
    long countWithEnrollment();

    @Query("""
            SELECT COUNT(course)
            FROM Course course
            WHERE course.onlineCourse IS TRUE
            """)
    long countOnlineCourses();

    @Query("""
            SELECT COUNT(course)
            FROM Course course
            WHERE course.athenaConfig IS NOT NULL
                AND (course.athenaConfig.gradingFeedbackEnabled IS TRUE OR course.athenaConfig.formativeFeedbackEnabled IS TRUE)
            """)
    long countWithAthenaFeedbackEnabled();

    @Query("""
            SELECT COUNT(course)
            FROM Course course
            WHERE course.testCourse IS TRUE
            """)
    long countTestCourses();
}
