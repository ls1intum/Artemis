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

    @Query(value = """
            SELECT COUNT(e.id)
            FROM PersistentAuditEvent e
            WHERE e.audit_event_date BETWEEN :fromDate AND :toDate
            """, nativeQuery = true)
    long countAllByAuditEventDateBetween(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate);

    @Query(value = """
            SELECT * FROM (
                SELECT
                    e.event_id AS event_id,
                    e.*,
                    ROW_NUMBER() OVER (ORDER BY e.event_id) AS rn
                FROM PersistentAuditEvent e
                LEFT JOIN jhi_persistent_audit_evt_data d ON e.event_id = d.event_id
                WHERE e.audit_event_date BETWEEN :fromDate AND :toDate
            ) sub
            WHERE sub.rn BETWEEN :from AND :to
            """, nativeQuery = true)
    List<PersistentAuditEvent> findAllByAuditEventDateBetweenWithRowBounds(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate, @Param("from") int from,
            @Param("to") int to);

    default Page<PersistentAuditEvent> findAllByAuditEventDateBetween(Instant fromDate, Instant toDate, Pageable pageable) {

        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int from = pageNumber * pageSize + 1;
        int to = (pageNumber + 1) * pageSize;

        List<PersistentAuditEvent> events = findAllByAuditEventDateBetweenWithRowBounds(fromDate, toDate, from, to);

        long count = countAllByAuditEventDateBetween(fromDate, toDate);

        return new PageImpl<>(events, pageable, count);
    }

    @Query(value = """
            SELECT COUNT(e.id)
            FROM PersistentAuditEvent e
            """, nativeQuery = true)
    long countAll();

    @Query(value = """
            SELECT * FROM (
                SELECT
                    e.event_id AS event_id,
                    e.*,
                    ROW_NUMBER() OVER (ORDER BY e.event_id) AS rn
                FROM PersistentAuditEvent e
                LEFT JOIN jhi_persistent_audit_evt_data d ON e.event_id = d.event_id
            ) sub
            WHERE sub.rn BETWEEN :from AND :to
            """, nativeQuery = true)
    List<PersistentAuditEvent> findAllWithRowBounds(@Param("from") int from, @Param("to") int to);

    default Page<PersistentAuditEvent> findAll(@NotNull Pageable pageable) {

        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int from = pageNumber * pageSize + 1;
        int to = (pageNumber + 1) * pageSize;

        List<PersistentAuditEvent> events = findAllWithRowBounds(from, to);

        long count = countAll();

        return new PageImpl<>(events, pageable, count);
    }

    @NotNull
    @EntityGraph(type = LOAD, attributePaths = { "data" })
    Optional<PersistentAuditEvent> findById(@NotNull Long auditEventId);
}
