package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreeDay;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupFreeDayRepository extends JpaRepository<TutorialGroupFreeDay, Long> {

    @Query("""
            SELECT tutorialGroupFreeDay
            FROM TutorialGroupFreeDay tutorialGroupFreeDay
            WHERE tutorialGroupFreeDay.date = :#{#date} AND tutorialGroupFreeDay.tutorialGroupsConfiguration.course = :#{#course}""")
    Set<TutorialGroupFreeDay> onDate(@Param("course") Course course, @Param("date") LocalDate date);

    default TutorialGroupFreeDay findByIdElseThrow(Long tutorialGroupFreeDayId) {
        return findById(tutorialGroupFreeDayId).orElseThrow(() -> new EntityNotFoundException("TutorialGroupFreeDay", tutorialGroupFreeDayId));
    }

}
