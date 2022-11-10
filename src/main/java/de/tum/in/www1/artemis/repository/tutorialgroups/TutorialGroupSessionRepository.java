package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupSessionRepository extends JpaRepository<TutorialGroupSession, Long> {

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.tutorialGroup.id = :#{#tutorialGroupId}
            AND tutorialGroupSession.status = :#{#status}
            AND tutorialGroupSession.start >= :#{#now}
            ORDER BY tutorialGroupSession.start""")
    List<TutorialGroupSession> findNextSessionsOfStatus(@Param("tutorialGroupId") Long tutorialGroupId, @Param("now") ZonedDateTime now,
            @Param("status") TutorialGroupSessionStatus status);

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.tutorialGroup.id = :#{#tutorialGroupId}""")
    Set<TutorialGroupSession> findAllByTutorialGroupId(@Param("tutorialGroupId") Long tutorialGroupId);

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.start <= :#{#end} AND tutorialGroupSession.end >= :#{#start}
            AND tutorialGroupSession.tutorialGroup = :#{#tutorialGroup}""")
    Set<TutorialGroupSession> findOverlappingInSameTutorialGroup(@Param("tutorialGroup") TutorialGroup tutorialGroup, @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.start <= :#{#end} AND tutorialGroupSession.end >= :#{#start}
            AND tutorialGroupSession.tutorialGroupSchedule IS NULL
            AND tutorialGroupSession.tutorialGroup = :#{#tutorialGroup}""")
    Set<TutorialGroupSession> findOverlappingIndividualSessionsInSameTutorialGroup(@Param("tutorialGroup") TutorialGroup tutorialGroup, @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);

    @Query("""
            SELECT tutorialGroupSession
            FROM TutorialGroupSession tutorialGroupSession
            WHERE tutorialGroupSession.start <= :#{#end} AND tutorialGroupSession.end >= :#{#start}
            AND tutorialGroupSession.tutorialGroup.course = :#{#course}""")
    Set<TutorialGroupSession> findAllBetween(@Param("course") Course course, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    Set<TutorialGroupSession> findAllByTutorialGroupFreePeriodId(Long freePeriodId);

    default TutorialGroupSession findByIdElseThrow(long tutorialGroupSessionId) {
        return findById(tutorialGroupSessionId).orElseThrow(() -> new EntityNotFoundException("TutorialGroupSession", tutorialGroupSessionId));
    }

    @Modifying
    @Transactional
    void deleteByTutorialGroupCourse(Course course);

    @Modifying
    @Transactional
    void deleteByTutorialGroupSchedule(TutorialGroupSchedule tutorialGroupSchedule);

}
