package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.audit.AuditEventConverter;
import de.tum.in.www1.artemis.repository.PersistenceAuditEventRepository;

/**
 * Service for managing audit events.
 * <p>
 * This is the default implementation to support SpringBoot Actuator AuditEventRepository
 */
@Profile(PROFILE_CORE)
@Service
public class AuditEventService {

    private final PersistenceAuditEventRepository persistenceAuditEventRepository;

    private final AuditEventConverter auditEventConverter;

    public AuditEventService(PersistenceAuditEventRepository persistenceAuditEventRepository, AuditEventConverter auditEventConverter) {
        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
        this.auditEventConverter = auditEventConverter;
    }

    public Page<AuditEvent> findAll(Pageable pageable) {
        return persistenceAuditEventRepository.findAllWithData(pageable).map(auditEventConverter::convertToAuditEvent);
    }

    public Page<AuditEvent> findByDates(Instant fromDate, Instant toDate, Pageable pageable) {
        return persistenceAuditEventRepository.findAllWithDataByAuditEventDateBetween(fromDate, toDate, pageable).map(auditEventConverter::convertToAuditEvent);
    }

    public Optional<AuditEvent> find(Long id) {
        return persistenceAuditEventRepository.findById(id).map(auditEventConverter::convertToAuditEvent);
    }
}
