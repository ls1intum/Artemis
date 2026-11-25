package de.tum.cit.aet.artemis.admin.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Instant;
import java.util.Collection;
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

import de.tum.cit.aet.artemis.admin.domain.SecurityAuditEvent;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for {@link SecurityAuditEvent} (the security audit log).
 * <p>
 * The paging methods mirror {@link PersistenceAuditEventRepository}: ids are fetched first and the rows are then loaded
 * with their {@code data} collection via an entity graph, because paging a query that joins a collection cannot be done
 * in SQL without duplicating parent rows.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface SecurityAuditEventRepository extends ArtemisJpaRepository<SecurityAuditEvent, Long> {

    /**
     * Finds a bounded page of expired event ids, oldest first, so pruning can run in batches.
     *
     * @param before   only events strictly older than this are returned
     * @param pageable bounds the batch size
     * @return ids of expired events, oldest first
     */
    @Query("""
            SELECT event.id
            FROM SecurityAuditEvent event
            WHERE event.auditEventDate < :before
            ORDER BY event.auditEventDate ASC
            """)
    List<Long> findExpiredIds(@Param("before") Instant before, Pageable pageable);

    @Query("""
            SELECT event.id
            FROM SecurityAuditEvent event
            WHERE event.auditEventDate BETWEEN :fromDate AND :toDate
            """)
    List<Long> findIdsByAuditEventDateBetween(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "data" })
    List<SecurityAuditEvent> findByPrincipalAndAuditEventDateAfterAndAuditEventType(String principal, Instant after, String type);

    @EntityGraph(type = LOAD, attributePaths = { "data" })
    List<SecurityAuditEvent> findByPrincipalAndAuditEventDateAfter(String principal, Instant after);

    @EntityGraph(type = LOAD, attributePaths = "data")
    List<SecurityAuditEvent> findWithDataByIdIn(List<Long> ids);

    long countByAuditEventDateBetween(Instant fromDate, Instant toDate);

    @Query("""
            SELECT event.id
            FROM SecurityAuditEvent event
            """)
    List<Long> findAllIds(Pageable pageable);

    @Query("""
            SELECT p.id
            FROM SecurityAuditEvent p
            WHERE p.principal IN :principals
                AND p.auditEventDate BETWEEN :fromDate AND :toDate
            """)
    List<Long> findIdsByPrincipalInAndAuditEventDateBetween(@Param("principals") Collection<String> principals, @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate, Pageable pageable);

    long countByPrincipalInAndAuditEventDateBetween(Collection<String> principals, Instant fromDate, Instant toDate);

    /**
     * Retrieves a page of the events of a principal whose date lies in the given range. The principal is matched exactly, so the query is served by the index on principal and
     * date.
     *
     * @param principals the spellings of the principal (login) to read the events of
     * @param fromDate   the start of the date range (inclusive)
     * @param toDate     the end of the date range
     * @param pageable   the pagination information
     * @return a page of events with their data, or an empty page if there are none
     */
    default Page<SecurityAuditEvent> findAllWithDataByPrincipalInAndAuditEventDateBetween(Collection<String> principals, Instant fromDate, Instant toDate, Pageable pageable) {
        List<Long> ids = findIdsByPrincipalInAndAuditEventDateBetween(principals, fromDate, toDate, pageable);
        if (ids.isEmpty()) {
            // a page after the last one is empty, but the total still counts the events before it
            return pageable.isUnpaged() || pageable.getOffset() == 0 ? Page.empty(pageable)
                    : new PageImpl<>(List.of(), pageable, countByPrincipalInAndAuditEventDateBetween(principals, fromDate, toDate));
        }
        List<SecurityAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, countByPrincipalInAndAuditEventDateBetween(principals, fromDate, toDate));
    }

    @Query("""
            SELECT p.id
            FROM SecurityAuditEvent p
            WHERE p.principal IN :principals
            """)
    List<Long> findIdsByPrincipalIn(@Param("principals") Collection<String> principals, Pageable pageable);

    long countByPrincipalIn(Collection<String> principals);

    /**
     * Retrieves a page of the events of a principal. The principal is matched exactly, so the query is served by the index on principal and date.
     *
     * @param principals the spellings of the principal (login) to read the events of
     * @param pageable   the pagination information
     * @return a page of events with their data, or an empty page if there are none
     */
    default Page<SecurityAuditEvent> findAllWithDataByPrincipalIn(Collection<String> principals, Pageable pageable) {
        List<Long> ids = findIdsByPrincipalIn(principals, pageable);
        if (ids.isEmpty()) {
            // a page after the last one is empty, but the total still counts the events before it
            return pageable.isUnpaged() || pageable.getOffset() == 0 ? Page.empty(pageable) : new PageImpl<>(List.of(), pageable, countByPrincipalIn(principals));
        }
        List<SecurityAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, countByPrincipalIn(principals));
    }

    @NonNull
    @EntityGraph(type = LOAD, attributePaths = { "data" })
    Optional<SecurityAuditEvent> findById(@NonNull Long auditEventId);

    /**
     * Retrieves a paginated list of events whose date lies in the given range, with their data eagerly loaded.
     *
     * @param fromDate the start of the range (inclusive)
     * @param toDate   the end of the range (inclusive)
     * @param pageable the pagination information
     * @return a page of matching events, empty if none match
     */
    default Page<SecurityAuditEvent> findAllWithDataByAuditEventDateBetween(Instant fromDate, Instant toDate, Pageable pageable) {
        List<Long> ids = findIdsByAuditEventDateBetween(fromDate, toDate, pageable);
        if (ids.isEmpty()) {
            // a page after the last one is empty, but the total still counts the events before it
            return pageable.isUnpaged() || pageable.getOffset() == 0 ? Page.empty(pageable) : new PageImpl<>(List.of(), pageable, countByAuditEventDateBetween(fromDate, toDate));
        }
        List<SecurityAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, countByAuditEventDateBetween(fromDate, toDate));
    }

    /**
     * Retrieves a paginated list of events with their data eagerly loaded.
     *
     * @param pageable the pagination information
     * @return a page of events, empty if none exist
     */
    default Page<SecurityAuditEvent> findAllWithData(@NonNull Pageable pageable) {
        List<Long> ids = findAllIds(pageable);
        if (ids.isEmpty()) {
            // a page after the last one is empty, but the total still counts the events before it
            return pageable.isUnpaged() || pageable.getOffset() == 0 ? Page.empty(pageable) : new PageImpl<>(List.of(), pageable, count());
        }
        List<SecurityAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, count());
    }
}
