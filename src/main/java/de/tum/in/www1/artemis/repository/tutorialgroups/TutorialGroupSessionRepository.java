package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.time.ZonedDateTime;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupSessionRepository extends JpaRepository<TutorialGroupSession, Long> {

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession LEFT JOIN tutorialGroupSession.tutorialGroupSchedule tutorialGroupSchedule
            WHERE tutorialGroupSession.tutorialGroup.course = :#{#course}""")
    Set<TutorialGroupSession> findAllOfCourse(@Param("course") Course course);

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.tutorialGroup.id = :#{#tutorialGroupId}""")
    Set<TutorialGroupSession> findAllByTutorialGroupId(@Param("tutorialGroupId") Long tutorialGroupId);

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.start <= :#{#end} AND tutorialGroupSession.end >= :#{#start}
            AND tutorialGroupSession.status = de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus.ACTIVE
                AND tutorialGroupSession.tutorialGroup.course = :#{#course}""")
    Set<TutorialGroupSession> findAllActiveBetween(@Param("course") Course course, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.start <= :#{#end} AND tutorialGroupSession.end >= :#{#start}
            AND tutorialGroupSession.status = de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus.CANCELLED
            AND tutorialGroupSession.tutorialGroup.course = :#{#course}""")
    Set<TutorialGroupSession> findAllCancelledBetween(@Param("course") Course course, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    default TutorialGroupSession findByIdElseThrow(long tutorialGroupSessionId) {
        return findById(tutorialGroupSessionId).orElseThrow(() -> new EntityNotFoundException("TutorialGroupSession", tutorialGroupSessionId));
    }

    @Modifying
    @Transactional
    void deleteByTutorialGroup_Course(Course course);

}
