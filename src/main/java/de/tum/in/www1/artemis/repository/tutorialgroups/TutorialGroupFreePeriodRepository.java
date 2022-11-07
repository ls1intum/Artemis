package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupFreePeriodRepository extends JpaRepository<TutorialGroupFreePeriod, Long> {

    @Query("""
            SELECT tutorialGroupFreePeriod
            FROM TutorialGroupFreePeriod tutorialGroupFreePeriod
            WHERE tutorialGroupFreePeriod.start <= :#{#toInclusive} AND tutorialGroupFreePeriod.end >= :#{#fromInclusive}
            AND tutorialGroupFreePeriod.tutorialGroupsConfiguration.course = :#{#course}""")
    Optional<TutorialGroupFreePeriod> findOverlappingInSameCourse(@Param("course") Course course, @Param("fromInclusive") ZonedDateTime fromInclusive,
            @Param("toInclusive") ZonedDateTime toInclusive);

    default TutorialGroupFreePeriod findByIdElseThrow(Long tutorialGroupFreePeriodId) {
        return findById(tutorialGroupFreePeriodId).orElseThrow(() -> new EntityNotFoundException("TutorialGroupFreePeriod", tutorialGroupFreePeriodId));
    }

    Set<TutorialGroupFreePeriod> findAllByTutorialGroupsConfigurationCourseId(Long courseId);

}
