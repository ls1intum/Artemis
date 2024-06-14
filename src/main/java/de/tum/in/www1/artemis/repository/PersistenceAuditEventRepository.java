package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.PersistentAuditEvent;

/**
 * Spring Data JPA repository for the PersistentAuditEvent entity.
 */
public interface PersistenceAuditEventRepository extends JpaRepository<PersistentAuditEvent, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "data" })
    List<PersistentAuditEvent> findByPrincipalAndAuditEventDateAfterAndAuditEventType(String principle, Instant after, String type);

    @Query("""
            SELECT p.id
            FROM PersistentAuditEvent p
            WHERE p.auditEventDate BETWEEN :fromDate AND :toDate
            """)
    List<Long> findIdsByAuditEventDateBetween(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate, Pageable pageable);

    @Query("""
            SELECT p
            FROM PersistentAuditEvent p
            LEFT JOIN FETCH p.data
            WHERE p.id IN :ids
            """)
    List<PersistentAuditEvent> findByIdsWithAssociations(@Param("ids") List<Long> ids);

    @Query("""
            SELECT COUNT(p)
            FROM PersistentAuditEvent p
            WHERE p.auditEventDate BETWEEN :fromDate AND :toDate
            """)
    long countByAuditEventDateBetween(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate);

    /**
     * Retrieves a paginated list of {@link PersistentAuditEvent} entities that have an audit event date between the specified fromDate and toDate.
     *
     * @param fromDate the start date of the audit event date range (inclusive).
     * @param toDate   the end date of the audit event date range (inclusive).
     * @param pageable the pagination information.
     * @return a paginated list of {@link PersistentAuditEvent} entities within the specified date range. If no entities are found, returns an empty page.
     */
    default Page<PersistentAuditEvent> findAllByAuditEventDateBetween(Instant fromDate, Instant toDate, Pageable pageable) {
        List<Long> ids = findIdsByAuditEventDateBetween(fromDate, toDate, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<PersistentAuditEvent> result = findByIdsWithAssociations(ids);
        return new PageImpl<>(result, pageable, countByAuditEventDateBetween(fromDate, toDate));
    }

    @Query("""
            SELECT p.id
            FROM PersistentAuditEvent p
            """)
    List<Long> findAllIds(Pageable pageable);

    @Query("""
            SELECT COUNT(p)
            FROM PersistentAuditEvent p
            """)
    long countAll();

    /**
     * Retrieves a paginated list of {@link PersistentAuditEvent} entities.
     *
     * @param pageable the pagination information.
     * @return a paginated list of {@link PersistentAuditEvent} entities. If no entities are found, returns an empty page.
     */
    default Page<PersistentAuditEvent> findAll(@NotNull Pageable pageable) {
        List<Long> ids = findAllIds(pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<PersistentAuditEvent> result = findByIdsWithAssociations(ids);
        return new PageImpl<>(result, pageable, countAll());
    }

    @NotNull
    @EntityGraph(type = LOAD, attributePaths = { "data" })
    Optional<PersistentAuditEvent> findById(@NotNull Long auditEventId);
}
