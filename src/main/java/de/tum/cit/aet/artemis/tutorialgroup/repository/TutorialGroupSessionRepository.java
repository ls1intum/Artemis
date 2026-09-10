package de.tum.cit.aet.artemis.tutorialgroup.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupFreePeriodSessionCountDTO;
import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailSessionDTO;

@Conditional(TutorialGroupEnabled.class)
@Lazy
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
                SELECT new de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO(
                    CONCAT('tutorialStartAndEndEvent-', CAST(session.id AS string)),
                    de.tum.cit.aet.artemis.calendar.util.CalendarEventType.TUTORIAL,
                    tutorialGroup.title,
                    session.start,
                    session.end,
                    CONCAT(
                        CAST(session.location AS string),
                        CASE WHEN tutorialGroup.campus IS NOT NULL AND NOT tutorialGroup.isOnline
                             THEN CONCAT(' - ', CAST(tutorialGroup.campus AS string))
                             ELSE ''
                        END
                    ),
                    CONCAT(teachingAssistant.firstName, ' ', teachingAssistant.lastName)
                )
                FROM TutorialGroupSession session
                    JOIN session.tutorialGroup tutorialGroup
                    JOIN tutorialGroup.teachingAssistant teachingAssistant
                WHERE tutorialGroup.id IN :tutorialGroupIds AND session.status = 'ACTIVE'
            """)
    Set<CalendarEventDTO> getCalendarEventDTOsFromActiveSessionsForTutorialGroupIds(@Param("tutorialGroupIds") Set<Long> tutorialGroupIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailSessionDTO(
                session.id,
                session.start,
                session.end,
                session.location,
                CASE
                    WHEN session.status = de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus.CANCELLED
                    THEN TRUE
                    ELSE FALSE
                END,
                CASE
                    WHEN session.tutorialGroupFreePeriod IS NOT NULL
                    THEN TRUE
                    ELSE FALSE
                END,
                session.attendanceCount
            )
            FROM TutorialGroupSession session
                JOIN session.tutorialGroup tutorialGroup
                LEFT JOIN session.tutorialGroupFreePeriod
            WHERE tutorialGroup.id = :tutorialGroupId
            ORDER BY session.start ASC
            """)
    List<RawTutorialGroupDetailSessionDTO> getTutorialGroupDetailSessionData(@Param("tutorialGroupId") long tutorialGroupId);

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
            WHERE session.start < :end
                AND session.end > :start
                AND session.tutorialGroup.course = :course
            """)
    Set<TutorialGroupSession> findAllBetween(@Param("course") Course course, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    /**
     * Returns the start of every session of a course that begins in the given span, ordered.
     * <p>
     * Only the start is selected because the caller counts sessions per calendar day and nothing else about the session
     * matters for that. Grouping by day is deliberately left to the caller rather than expressed in the query: the day a
     * session falls on depends on the time zone of the tutorial groups configuration, and a database-side date
     * conversion would key the count off the server's zone instead.
     *
     * @param course the course whose sessions are counted
     * @param start  the inclusive start of the span
     * @param end    the exclusive end of the span
     * @return the start of each session in the span, ascending
     */
    @Query("""
            SELECT session.start
            FROM TutorialGroupSession session
            WHERE session.tutorialGroup.course = :course
                AND session.start >= :start
                AND session.start < :end
            ORDER BY session.start
            """)
    List<ZonedDateTime> findSessionStartsBetween(@Param("course") Course course, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

    /**
     * Counts the sessions of a course that saving a holiday over this span would cancel.
     *
     * Overlap alone is not the answer, because cancelling only touches sessions that are still active - a session
     * another holiday already cancelled would be counted twice over. Editing is the exception: a holiday releases the
     * sessions it had cancelled before it takes them again, so its own are counted as well and the number does not
     * collapse to zero when a saved holiday is reopened unchanged.
     *
     * The join is spelled out and left outer on purpose: most sessions have no holiday against them, and navigating
     * that association inline would leave whether they survive the query up to how the association is resolved.
     *
     * @param course             the course whose sessions are counted
     * @param start              the start of the span
     * @param end                the end of the span
     * @param editedFreePeriodId the holiday being edited, whose own cancelled sessions still count; null when creating
     * @return how many sessions saving would cancel
     */
    @Query("""
            SELECT COUNT(session)
            FROM TutorialGroupSession session
                LEFT JOIN session.tutorialGroupFreePeriod cancelledBy
            WHERE session.tutorialGroup.course = :course
                AND session.start < :end
                AND session.end > :start
                AND (session.status = de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus.ACTIVE
                    OR (:editedFreePeriodId IS NOT NULL AND cancelledBy.id = :editedFreePeriodId))
            """)
    long countCancellableSessions(@Param("course") Course course, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end,
            @Param("editedFreePeriodId") Long editedFreePeriodId);

    /**
     * Counts, for every free period of a course, how many of its sessions that period covers.
     *
     * One query rather than one per holiday, and a left join so a period covering nothing still answers with zero
     * instead of dropping out of the result.
     *
     * @param course the course whose free periods and sessions are counted
     * @return one entry per free period of the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupFreePeriodSessionCountDTO(period.id, COUNT(session))
            FROM TutorialGroupFreePeriod period
                LEFT JOIN TutorialGroupSession session
                    ON session.tutorialGroup.course = period.tutorialGroupsConfiguration.course
                    AND session.start < period.end
                    AND session.end > period.start
            WHERE period.tutorialGroupsConfiguration.course = :course
            GROUP BY period.id
            """)
    List<TutorialGroupFreePeriodSessionCountDTO> countOverlappingSessionsPerFreePeriod(@Param("course") Course course);

    @Transactional // ok because of delete
    @Modifying
    void deleteByTutorialGroupCourse(Course course);

    @Transactional // ok because of delete
    @Modifying
    void deleteByTutorialGroupSchedule(TutorialGroupSchedule tutorialGroupSchedule);

    @Transactional // ok because of delete
    @Modifying
    void deleteByTutorialGroupId(Long tutorialGroupId);

}
