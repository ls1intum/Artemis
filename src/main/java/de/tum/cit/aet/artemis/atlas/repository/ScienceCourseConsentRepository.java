package de.tum.cit.aet.artemis.atlas.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceCourseConsent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEnabledCourse;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface ScienceCourseConsentRepository extends ArtemisJpaRepository<ScienceCourseConsent, Long> {

    Optional<ScienceCourseConsent> findByUserIdAndCourseId(long userId, long courseId);

    @Query("""
            SELECT consent
            FROM ScienceCourseConsent consent
                JOIN FETCH consent.course
            WHERE consent.user.id = :userId
                AND consent.course.id IN :courseIds
            """)
    List<ScienceCourseConsent> findAllByUserIdAndCourseIdIn(@Param("userId") long userId, @Param("courseIds") Set<Long> courseIds);

    /**
     * Whether the user has active consent for a course that currently collects science data.
     *
     * @param login    the user login, which is what a science event is keyed by
     * @param courseId the id of the course
     * @return true if the course is enabled for science collection and the user has consented to it
     */
    @Query("""
            SELECT COUNT(consent) > 0
            FROM ScienceCourseConsent consent
                JOIN ScienceEnabledCourse enabledCourse
                    ON enabledCourse.course.id = consent.course.id
            WHERE consent.user.login = :login
                AND consent.course.id = :courseId
                AND consent.active = TRUE
                AND enabledCourse.active = TRUE
            """)
    boolean existsActiveConsentForEnabledCourse(@Param("login") String login, @Param("courseId") long courseId);

    /**
     * Takes a write lock on the science enablement of a course, so that the consent decisions in the course are stored one
     * after the other. The lock is held until the surrounding transaction ends, see {@link #saveDecision}.
     * <p>
     * The enablement rather than the consent is locked, because it is there for every decision: creating or activating a
     * consent needs an enabled course, and disabling a course keeps its row. A student's first decision has no consent row
     * yet that could be locked.
     *
     * @param courseId the id of the course
     * @return the locked enablement, if the course was ever enabled
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT enabledCourse
            FROM ScienceEnabledCourse enabledCourse
            WHERE enabledCourse.course.id = :courseId
            """)
    Optional<ScienceEnabledCourse> findEnabledCourseByCourseIdForUpdate(@Param("courseId") long courseId);

    /**
     * The consent decisions recorded on the science timeline of a user in a course, the latest first.
     *
     * @param identity      the login the events are recorded under
     * @param courseId      the id of the course
     * @param decisionTypes the event types that record a consent decision
     * @param pageable      the page to return, usually only the latest decision
     * @return the recorded decision types, the latest first
     */
    @Query("""
            SELECT event.type
            FROM ScienceEvent event
            WHERE event.identity = :identity
                AND event.courseId = :courseId
                AND event.type IN :decisionTypes
            ORDER BY event.timestamp DESC, event.id DESC
            """)
    List<ScienceEventType> findLatestDecisionTypes(@Param("identity") String identity, @Param("courseId") long courseId,
            @Param("decisionTypes") Set<ScienceEventType> decisionTypes, Pageable pageable);

    /**
     * Stores a student's consent decision for a course together with the marker that records it on the science timeline.
     * <p>
     * Two decisions of a student can reach the server together, for example from a double click on the switch. Stored
     * independently, an opt-in could then leave the consent active while the latest marker records the opt-out that ran in
     * between. Under the course's lock they run one after the other, and the consent is read after the lock is taken, so a
     * decision that waited sees what the one before it stored.
     * <p>
     * The marker is written only when the timeline does not already end with this decision. Saving the same decision twice
     * therefore records it once, and a marker that went missing is written again by the next save.
     *
     * @param user         the student who decides
     * @param course       the course the decision is for
     * @param active       whether the student contributes science data from now on
     * @param recordMarker stores the marker of the decision; it runs inside this transaction
     * @return the stored consent
     */
    @Transactional // ok: the lock, the read of the latest decision and both writes are only consistent as one unit
    default ScienceCourseConsent saveDecision(User user, Course course, boolean active, Consumer<ScienceEvent> recordMarker) {
        findEnabledCourseByCourseIdForUpdate(course.getId());
        ScienceCourseConsent consent = findByUserIdAndCourseId(user.getId(), course.getId()).orElseGet(ScienceCourseConsent::new);
        consent.setUser(user);
        consent.setCourse(course);
        consent.setActive(active);
        ScienceCourseConsent savedConsent = save(consent);

        ScienceEventType decision = active ? ScienceEventType.SCIENCE__OPT_IN : ScienceEventType.SCIENCE__OPT_OUT;
        boolean alreadyRecorded = findLatestDecisionTypes(user.getLogin(), course.getId(), ScienceEventType.CONSENT_DECISION_EVENT_TYPES, PageRequest.ofSize(1)).stream()
                .findFirst().filter(decision::equals).isPresent();
        if (!alreadyRecorded) {
            recordMarker.accept(ScienceEvent.of(user.getLogin(), decision, null, course.getId(), ZonedDateTime.now()));
        }
        return savedConsent;
    }
}
