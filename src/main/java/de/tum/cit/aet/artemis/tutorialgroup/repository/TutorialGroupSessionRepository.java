package de.tum.cit.aet.artemis.tutorialgroup.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

@Profile(PROFILE_CORE)
@Repository
public interface TutorialGroupSessionRepository extends ArtemisJpaRepository<TutorialGroupSession, Long> {

    @Query("""
            SELECT session
            FROM TutorialGroupSession session
            WHERE session.tutorialGroup.id = :tutorialGroupId
                AND session.status = :status
                AND session.start >= :now
            ORDER BY session.start
            """)
    List<TutorialGroupSession> findNextSessionsOfStatus(@Param("tutorialGroupId") Long tutorialGroupId, @Param("now") ZonedDateTime now,
            @Param("status") TutorialGroupSessionStatus status);

    @Query("""
            SELECT session
            FROM TutorialGroupSession session
            WHERE session.tutorialGroup.id = :tutorialGroupId
            """)
    Set<TutorialGroupSession> findAllByTutorialGroupId(@Param("tutorialGroupId") Long tutorialGroupId);

    @Query("""
            SELECT session
            FROM TutorialGroupSession session
            WHERE session.tutorialGroupSchedule.id = :scheduleId
            """)
    Set<TutorialGroupSession> findAllByScheduleId(@Param("scheduleId") Long scheduleId);

    @Query("""
            SELECT session
            FROM TutorialGroupSession session
            WHERE session.start <= :end
                AND session.end >= :start
                AND session.tutorialGroup = :tutorialGroup
            """)
    Set<TutorialGroupSession> findOverlappingInSameTutorialGroup(@Param("tutorialGroup") TutorialGroup tutorialGroup, @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);

    @Query("""
            SELECT session
            FROM TutorialGroupSession session
            WHERE session.start <= :end
                AND session.end >= :start
                AND session.tutorialGroupSchedule IS NULL
                AND session.tutorialGroup = :tutorialGroup
            """)
    Set<TutorialGroupSession> findOverlappingIndividualSessionsInSameTutorialGroup(@Param("tutorialGroup") TutorialGroup tutorialGroup, @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);

    @Query("""
            SELECT session
            FROM TutorialGroupSession session
            WHERE session.start < :end
                AND session.end > :start
                AND session.tutorialGroup.course = :course
            """)
    Set<TutorialGroupSession> findAllBetween(@Param("course") Course course, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    @Transactional // ok because of delete
    @Modifying
    void deleteByTutorialGroupCourse(Course course);

    @Transactional // ok because of delete
    @Modifying
    void deleteByTutorialGroupSchedule(TutorialGroupSchedule tutorialGroupSchedule);

}
