package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.PersistentAuditEvent;

/**
 * Spring Data JPA repository for the PersistentAuditEvent entity.
 */
public interface PersistenceAuditEventRepository extends JpaRepository<PersistentAuditEvent, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "data" })
    List<PersistentAuditEvent> findByPrincipalAndAuditEventDateAfterAndAuditEventType(String principle, Instant after, String type);

    @EntityGraph(type = LOAD, attributePaths = { "data" })
    Page<PersistentAuditEvent> findAllByAuditEventDateBetween(Instant fromDate, Instant toDate, Pageable pageable);

    @NotNull
    @EntityGraph(type = LOAD, attributePaths = { "data" })
    Page<PersistentAuditEvent> findAll(@NotNull Pageable pageable);

    @NotNull
    @EntityGraph(type = LOAD, attributePaths = { "data" })
    Optional<PersistentAuditEvent> findById(@NotNull Long auditEventId);
}
