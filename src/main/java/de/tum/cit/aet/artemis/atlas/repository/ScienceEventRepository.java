package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.atlas.repository.ScienceEventSpecs.after;
import static de.tum.cit.aet.artemis.atlas.repository.ScienceEventSpecs.hasRecordedConsentDecision;
import static de.tum.cit.aet.artemis.atlas.repository.ScienceEventSpecs.hasTypeIn;
import static de.tum.cit.aet.artemis.atlas.repository.ScienceEventSpecs.idAtMost;
import static de.tum.cit.aet.artemis.atlas.repository.ScienceEventSpecs.inCourses;
import static de.tum.cit.aet.artemis.atlas.repository.ScienceEventSpecs.recordedFrom;
import static de.tum.cit.aet.artemis.atlas.repository.ScienceEventSpecs.recordedUntil;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the ScienceEvent entity.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface ScienceEventRepository extends ArtemisJpaRepository<ScienceEvent, Long>, JpaSpecificationExecutor<ScienceEvent> {

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ScienceEvent se
            SET se.identity = :newIdentity
            WHERE se.identity = :oldIdentity
            """)
    void renameIdentity(@Param("oldIdentity") String oldIdentity, @Param("newIdentity") String newIdentity);

    Set<ScienceEvent> findAllByIdentity(String identity);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            DELETE FROM ScienceEvent se
            WHERE se.identity = :identity
                AND se.courseId = :courseId
                AND se.type NOT IN :retainedTypes
            """)
    int deleteInteractionEventsByIdentityAndCourseId(@Param("identity") String identity, @Param("courseId") long courseId,
            @Param("retainedTypes") Set<ScienceEventType> retainedTypes);

    /**
     * Returns the type of the most recent consent decision recorded for a user in a course.
     * <p>
     * The consent row says what the current decision is; this says what the timeline last recorded. Comparing the two is
     * what lets a decision be written again without producing a second marker, and lets a marker lost to a failed write
     * be repaired by the next save.
     *
     * @param identity      the user login, which is what a science event is keyed by
     * @param courseId      the id of the course
     * @param decisionTypes the event types that record a consent decision
     * @param pageable      limits the result to the most recent decision
     * @return the most recently recorded decision, or empty when the user has never decided
     */
    @Query("""
            SELECT se.type
            FROM ScienceEvent se
            WHERE se.identity = :identity
                AND se.courseId = :courseId
                AND se.type IN :decisionTypes
            ORDER BY se.timestamp DESC, se.id DESC
            """)
    List<ScienceEventType> findLatestDecisionType(@Param("identity") String identity, @Param("courseId") long courseId, @Param("decisionTypes") Set<ScienceEventType> decisionTypes,
            Pageable pageable);

    /**
     * Returns the highest event id matching the export filter, which bounds the export to the rows that existed when it
     * started.
     *
     * @param courseIds  the courses to export
     * @param from       the earliest timestamp to include, or {@code null} for no lower bound
     * @param to         the latest timestamp to include, or {@code null} for no upper bound
     * @param eventTypes the event types to export
     * @return the highest matching event id, or empty when nothing matches
     */
    default Optional<Long> findMaxIdForResearchExport(Set<Long> courseIds, @Nullable ZonedDateTime from, @Nullable ZonedDateTime to, Set<ScienceEventType> eventTypes) {
        Specification<ScienceEvent> specification = researchExportFilter(courseIds, from, to, eventTypes);
        return findBy(specification, query -> query.project(DomainObject_.ID).sortBy(Sort.by(Sort.Direction.DESC, DomainObject_.ID)).first()).map(DomainObject::getId);
    }

    /**
     * Returns the next page of events for the research export, ordered by timestamp and id.
     *
     * @param courseIds     the courses to export
     * @param from          the earliest timestamp to include, or {@code null} for no lower bound
     * @param to            the latest timestamp to include, or {@code null} for no upper bound
     * @param eventTypes    the event types to export
     * @param maxEventId    the highest event id the export covers
     * @param lastTimestamp the timestamp of the last exported event, or {@code null} for the first page
     * @param lastId        the id of the last exported event, or {@code null} for the first page
     * @param pageSize      the maximum number of events to return
     * @return the next page of matching events
     */
    default List<ScienceEvent> findNextPageForResearchExport(Set<Long> courseIds, @Nullable ZonedDateTime from, @Nullable ZonedDateTime to, Set<ScienceEventType> eventTypes,
            long maxEventId, @Nullable ZonedDateTime lastTimestamp, @Nullable Long lastId, int pageSize) {
        Specification<ScienceEvent> specification = Specification
                .allOf(Stream.of(researchExportFilter(courseIds, from, to, eventTypes), idAtMost(maxEventId), after(lastTimestamp, lastId)).filter(Objects::nonNull).toList());
        return findBy(specification, query -> query.sortBy(Sort.by(Sort.Direction.ASC, "timestamp", DomainObject_.ID)).limit(pageSize).all());
    }

    private static Specification<ScienceEvent> researchExportFilter(Set<Long> courseIds, @Nullable ZonedDateTime from, @Nullable ZonedDateTime to,
            Set<ScienceEventType> eventTypes) {
        return Specification.allOf(Stream
                .of(inCourses(courseIds), hasTypeIn(eventTypes), hasRecordedConsentDecision(ScienceEventType.CONSENT_DECISION_EVENT_TYPES), recordedFrom(from), recordedUntil(to))
                .filter(Objects::nonNull).toList());
    }
}
