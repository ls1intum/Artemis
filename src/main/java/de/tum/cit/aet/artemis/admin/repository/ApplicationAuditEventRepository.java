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

import de.tum.cit.aet.artemis.admin.domain.ApplicationAuditEvent;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for {@link ApplicationAuditEvent} (the application audit log).
 * <p>
 * The paging methods mirror {@link PersistenceAuditEventRepository}: ids are fetched first and the rows are then loaded
 * with their {@code data} collection via an entity graph, because paging a query that joins a collection cannot be done
 * in SQL without duplicating parent rows.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ApplicationAuditEventRepository extends ArtemisJpaRepository<ApplicationAuditEvent, Long> {

    /**
     * Finds a bounded page of expired event ids, oldest first, so pruning can run in batches.
     *
     * @param before   only events strictly older than this are returned
     * @param pageable bounds the batch size
     * @return ids of expired events, oldest first
     */
    @Query("""
            SELECT event.id
            FROM ApplicationAuditEvent event
            WHERE event.auditEventDate < :before
            ORDER BY event.auditEventDate ASC
            """)
    List<Long> findExpiredIds(@Param("before") Instant before, Pageable pageable);

    @Query("""
            SELECT event.id
            FROM ApplicationAuditEvent event
            WHERE event.auditEventDate BETWEEN :fromDate AND :toDate
            """)
    List<Long> findIdsByAuditEventDateBetween(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = "data")
    List<ApplicationAuditEvent> findWithDataByIdIn(List<Long> ids);

    long countByAuditEventDateBetween(Instant fromDate, Instant toDate);

    @Query("""
            SELECT event.id
            FROM ApplicationAuditEvent event
            """)
    List<Long> findAllIds(Pageable pageable);

    @NonNull
    @EntityGraph(type = LOAD, attributePaths = { "data" })
    Optional<ApplicationAuditEvent> findById(@NonNull Long auditEventId);

    /**
     * Retrieves a paginated list of events whose date lies in the given range, with their data eagerly loaded.
     *
     * @param fromDate the start of the range (inclusive)
     * @param toDate   the end of the range (inclusive)
     * @param pageable the pagination information
     * @return a page of matching events, empty if none match
     */
    default Page<ApplicationAuditEvent> findAllWithDataByAuditEventDateBetween(Instant fromDate, Instant toDate, Pageable pageable) {
        List<Long> ids = findIdsByAuditEventDateBetween(fromDate, toDate, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<ApplicationAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, countByAuditEventDateBetween(fromDate, toDate));
    }

    /**
     * Retrieves a paginated list of events with their data eagerly loaded.
     *
     * @param pageable the pagination information
     * @return a page of events, empty if none exist
     */
    default Page<ApplicationAuditEvent> findAllWithData(@NonNull Pageable pageable) {
        List<Long> ids = findAllIds(pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<ApplicationAuditEvent> result = findWithDataByIdIn(ids);
        result.sort(Comparator.comparing(event -> ids.indexOf(event.getId())));
        return new PageImpl<>(result, pageable, count());
    }
}
