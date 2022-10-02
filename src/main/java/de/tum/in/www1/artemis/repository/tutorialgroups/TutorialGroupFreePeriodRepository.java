package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupFreePeriodRepository extends JpaRepository<TutorialGroupFreePeriod, Long> {

    @Query("""
            SELECT tutorialGroupFreePeriod
            FROM TutorialGroupFreePeriod tutorialGroupFreePeriod
            WHERE tutorialGroupFreePeriod.start <= :#{#to_inclusive} AND tutorialGroupFreePeriod.end >= :#{#from_inclusive}
            AND tutorialGroupFreePeriod.tutorialGroupsConfiguration.course = :#{#course}""")
    Optional<TutorialGroupFreePeriod> findOverlappingInSameCourse(@Param("course") Course course, @Param("from_inclusive") ZonedDateTime from_inclusive,
            @Param("to_inclusive") ZonedDateTime to_inclusive);

    default TutorialGroupFreePeriod findByIdElseThrow(Long tutorialGroupFreePeriodId) {
        return findById(tutorialGroupFreePeriodId).orElseThrow(() -> new EntityNotFoundException("TutorialGroupFreePeriod", tutorialGroupFreePeriodId));
    }

    List<TutorialGroupFreePeriod> findAllByTutorialGroupsConfigurationCourseId(Long courseId);

    @Modifying
    @Transactional
    void deleteByTutorialGroupsConfiguration_Course(Course course);

}
