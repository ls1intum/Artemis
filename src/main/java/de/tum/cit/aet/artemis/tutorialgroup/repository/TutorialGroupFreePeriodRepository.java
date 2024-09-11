package de.tum.cit.aet.artemis.tutorialgroup.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;

@Profile(PROFILE_CORE)
@Repository
public interface TutorialGroupFreePeriodRepository extends ArtemisJpaRepository<TutorialGroupFreePeriod, Long> {

    @Query("""
            SELECT period
            FROM TutorialGroupFreePeriod period
            WHERE period.start <= :toInclusive
                AND period.end >= :fromInclusive
                AND period.tutorialGroupsConfiguration.course = :course
            """)
    Set<TutorialGroupFreePeriod> findOverlappingInSameCourse(@Param("course") Course course, @Param("fromInclusive") ZonedDateTime fromInclusive,
            @Param("toInclusive") ZonedDateTime toInclusive);

    @Query("""
            SELECT period
            FROM TutorialGroupFreePeriod period
            WHERE period.start < :toExclusive
                AND period.end > :fromExclusive
                AND period.tutorialGroupsConfiguration.course = :course
            """)
    Set<TutorialGroupFreePeriod> findOverlappingInSameCourseExclusive(@Param("course") Course course, @Param("fromExclusive") ZonedDateTime fromExclusive,
            @Param("toExclusive") ZonedDateTime toExclusive);

    /**
     * Finds the first overlapping tutorial group free period in the same course.
     *
     * @param course        The course to check for overlapping periods. * @param fromInclusive The start date and time of the period to check.
     * @param fromInclusive The start date and time of the period to check.
     * @param toInclusive   The end date and time of the period to check.
     * @return An Optional containing the first overlapping TutorialGroupFreePeriod, if any.
     */
    default Optional<TutorialGroupFreePeriod> findFirstOverlappingInSameCourse(Course course, ZonedDateTime fromInclusive, ZonedDateTime toInclusive) {
        return findOverlappingInSameCourse(course, fromInclusive, toInclusive).stream().findFirst();
    }

    Set<TutorialGroupFreePeriod> findAllByTutorialGroupsConfigurationCourseId(Long courseId);

}
