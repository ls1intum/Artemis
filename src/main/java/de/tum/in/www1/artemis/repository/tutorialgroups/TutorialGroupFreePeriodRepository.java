package de.tum.in.www1.artemis.repository.tutorialgroups;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Repository
public interface TutorialGroupFreePeriodRepository extends JpaRepository<TutorialGroupFreePeriod, Long> {

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

    default TutorialGroupFreePeriod findByIdElseThrow(Long tutorialGroupFreePeriodId) {
        return findById(tutorialGroupFreePeriodId).orElseThrow(() -> new EntityNotFoundException("TutorialGroupFreePeriod", tutorialGroupFreePeriodId));
    }

    default Optional<TutorialGroupFreePeriod> findFirstOverlappingInSameCourse(Course course, ZonedDateTime fromInclusive, ZonedDateTime toInclusive) {
        return findOverlappingInSameCourse(course, fromInclusive, toInclusive).stream().findFirst();
    }

    Set<TutorialGroupFreePeriod> findAllByTutorialGroupsConfigurationCourseId(Long courseId);

}
