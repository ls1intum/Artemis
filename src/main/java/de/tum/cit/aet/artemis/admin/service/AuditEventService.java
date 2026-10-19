package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.repository.ApplicationAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.SecurityAuditEventRepository;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConverter;
import de.tum.cit.aet.artemis.core.config.audit.AuditLogType;

/**
 * Service for reading audit events.
 * <p>
 * Every method takes the {@link AuditLogType} to read from, so the admin view can query the three logs (general, security
 * and application) independently - the point of splitting them is that each tab scans only its own, much smaller table.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AuditEventService {

    private final PersistenceAuditEventRepository persistenceAuditEventRepository;

    private final SecurityAuditEventRepository securityAuditEventRepository;

    private final ApplicationAuditEventRepository applicationAuditEventRepository;

    private final AuditEventConverter auditEventConverter;

    public AuditEventService(PersistenceAuditEventRepository persistenceAuditEventRepository, SecurityAuditEventRepository securityAuditEventRepository,
            ApplicationAuditEventRepository applicationAuditEventRepository, AuditEventConverter auditEventConverter) {
        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
        this.securityAuditEventRepository = securityAuditEventRepository;
        this.applicationAuditEventRepository = applicationAuditEventRepository;
        this.auditEventConverter = auditEventConverter;
    }

    /**
     * Finds a page of audit events in the given log.
     *
     * @param logType  which audit log to read
     * @param pageable the pagination information
     * @return a page of audit events
     */
    public Page<AuditEvent> findAll(AuditLogType logType, Pageable pageable) {
        return switch (logType) {
            case GENERAL -> persistenceAuditEventRepository.findAllWithData(pageable).map(auditEventConverter::convertToAuditEvent);
            case SECURITY -> securityAuditEventRepository.findAllWithData(pageable).map(auditEventConverter::convertToAuditEvent);
            case APPLICATION -> applicationAuditEventRepository.findAllWithData(pageable).map(auditEventConverter::convertToAuditEvent);
        };
    }

    /**
     * Finds a page of audit events in the given log whose date lies in the given range.
     *
     * @param logType  which audit log to read
     * @param fromDate the start of the range
     * @param toDate   the end of the range
     * @param pageable the pagination information
     * @return a page of audit events
     */
    public Page<AuditEvent> findByDates(AuditLogType logType, Instant fromDate, Instant toDate, Pageable pageable) {
        return switch (logType) {
            case GENERAL -> persistenceAuditEventRepository.findAllWithDataByAuditEventDateBetween(fromDate, toDate, pageable).map(auditEventConverter::convertToAuditEvent);
            case SECURITY -> securityAuditEventRepository.findAllWithDataByAuditEventDateBetween(fromDate, toDate, pageable).map(auditEventConverter::convertToAuditEvent);
            case APPLICATION -> applicationAuditEventRepository.findAllWithDataByAuditEventDateBetween(fromDate, toDate, pageable).map(auditEventConverter::convertToAuditEvent);
        };
    }

    /**
     * Finds a single audit event by id in the given log. Ids are only unique within a log, so the log has to be specified.
     *
     * @param logType which audit log to read
     * @param id      the id of the event
     * @return the event, or empty if no event with that id exists in that log
     */
    public Optional<AuditEvent> find(AuditLogType logType, Long id) {
        return switch (logType) {
            case GENERAL -> persistenceAuditEventRepository.findById(id).map(auditEventConverter::convertToAuditEvent);
            case SECURITY -> securityAuditEventRepository.findById(id).map(auditEventConverter::convertToAuditEvent);
            case APPLICATION -> applicationAuditEventRepository.findById(id).map(auditEventConverter::convertToAuditEvent);
        };
    }
}
