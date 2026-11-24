package de.tum.cit.aet.artemis.admin.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.admin.domain.PersistentAuditEvent;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the PersistentAuditEvent entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PersistenceAuditEventRepository extends ArtemisJpaRepository<PersistentAuditEvent, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "data" })
    List<PersistentAuditEvent> findByPrincipalAndAuditEventDateAfterAndAuditEventType(String principle, Instant after, String type);

    @EntityGraph(type = LOAD, attributePaths = { "data" })
    List<PersistentAuditEvent> findByPrincipalAndAuditEventDateAfter(String principal, Instant after);

    /**
     * Finds a bounded page of expired event ids, oldest first, so pruning can run in batches. Batching matters because
     * the log was never pruned before, so the first run has to clear a backlog that may span years; deleting all of it in
     * one transaction would hold locks for a long time and bloat the undo log. Oldest first so that repeated runs make
     * monotonic progress instead of revisiting the same rows.
     * <p>
     * No type filter is needed: after the audit log split this table holds only authentication events, all of which share
     * the general retention period.
     *
     * @param before   only events strictly older than this are returned
     * @param pageable bounds the batch size
     * @return ids of expired events, oldest first
     */
    @Query("""
            SELECT event.id
            FROM PersistentAuditEvent event
            WHERE event.auditEventDate < :before
            ORDER BY event.auditEventDate ASC
            """)
    List<Long> findExpiredIds(@Param("before") Instant before, Pageable pageable);

    @Query("""
            SELECT p.id
            FROM PersistentAuditEvent p
            WHERE p.auditEventDate BETWEEN :fromDate AND :toDate
            """)
    List<Long> findIdsByAuditEventDateBetween(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "data")
    List<PersistentAuditEvent> findWithDataByIdIn(List<Long> ids);

    long countByAuditEventDateBetween(Instant fromDate, Instant toDate);

    /**
     * Retrieves a paginated list of {@link PersistentAuditEvent} entities that have an audit event date between the specified fromDate and toDate.
     *
     * @param fromDate the start date of the audit event date range (inclusive).
     * @param toDate   the end date of the audit event date range (inclusive).
     * @param pageable the pagination information.
     * @return a paginated list of {@link PersistentAuditEvent} entities within the specified date range. If no entities are found, returns an empty page.
     */
    default Page<PersistentAuditEvent> findAllWithDataByAuditEventDateBetween(Instant fromDate, Instant toDate, Pageable pageable) {
        List<Long> ids = findIdsByAuditEventDateBetween(fromDate, toDate, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<PersistentAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, countByAuditEventDateBetween(fromDate, toDate));
    }

    @Query("""
            SELECT p.id
            FROM PersistentAuditEvent p
            """)
    List<Long> findAllIds(Pageable pageable);

    /**
     * Retrieves a paginated list of {@link PersistentAuditEvent} entities.
     *
     * @param pageable the pagination information.
     * @return a paginated list of {@link PersistentAuditEvent} entities. If no entities are found, returns an empty page.
     */
    default Page<PersistentAuditEvent> findAllWithData(@NonNull Pageable pageable) {
        List<Long> ids = findAllIds(pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<PersistentAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, count());
    }

    @Query("""
            SELECT p.id
            FROM PersistentAuditEvent p
            WHERE p.principal = :principal
                AND p.auditEventDate BETWEEN :fromDate AND :toDate
            """)
    List<Long> findIdsByPrincipalAndAuditEventDateBetween(@Param("principal") String principal, @Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate,
            Pageable pageable);

    long countByPrincipalAndAuditEventDateBetween(String principal, Instant fromDate, Instant toDate);

    /**
     * Retrieves a page of the events of one principal whose date lies in the given range. The principal is matched exactly, so the query is served by the index on principal and
     * date.
     *
     * @param principal the principal (login) to read the events of
     * @param fromDate  the start of the date range (inclusive)
     * @param toDate    the end of the date range
     * @param pageable  the pagination information
     * @return a page of events with their data, or an empty page if there are none
     */
    default Page<PersistentAuditEvent> findAllWithDataByPrincipalAndAuditEventDateBetween(String principal, Instant fromDate, Instant toDate, Pageable pageable) {
        List<Long> ids = findIdsByPrincipalAndAuditEventDateBetween(principal, fromDate, toDate, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<PersistentAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, countByPrincipalAndAuditEventDateBetween(principal, fromDate, toDate));
    }

    @Query("""
            SELECT p.id
            FROM PersistentAuditEvent p
            WHERE p.principal = :principal
            """)
    List<Long> findIdsByPrincipal(@Param("principal") String principal, Pageable pageable);

    long countByPrincipal(String principal);

    /**
     * Retrieves a page of the events of one principal. The principal is matched exactly, so the query is served by the index on principal and date.
     *
     * @param principal the principal (login) to read the events of
     * @param pageable  the pagination information
     * @return a page of events with their data, or an empty page if there are none
     */
    default Page<PersistentAuditEvent> findAllWithDataByPrincipal(String principal, Pageable pageable) {
        List<Long> ids = findIdsByPrincipal(principal, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<PersistentAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, countByPrincipal(principal));
    }

    @NonNull
    @EntityGraph(type = LOAD, attributePaths = { "data" })
    Optional<PersistentAuditEvent> findById(@NonNull Long auditEventId);
}
