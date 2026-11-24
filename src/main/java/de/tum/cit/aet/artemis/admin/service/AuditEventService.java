package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
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
     * @param logType   which audit log to read
     * @param principal only the events of this principal (login) are returned, or all events if it is null or blank
     * @param pageable  the pagination information
     * @return a page of audit events
     */
    public Page<AuditEvent> findAll(AuditLogType logType, @Nullable String principal, Pageable pageable) {
        Collection<String> principals = principalSpellings(principal);
        return switch (logType) {
            case GENERAL -> (principals.isEmpty() ? persistenceAuditEventRepository.findAllWithData(pageable)
                    : persistenceAuditEventRepository.findAllWithDataByPrincipalIn(principals, pageable)).map(auditEventConverter::convertToAuditEvent);
            case SECURITY ->
                (principals.isEmpty() ? securityAuditEventRepository.findAllWithData(pageable) : securityAuditEventRepository.findAllWithDataByPrincipalIn(principals, pageable))
                        .map(auditEventConverter::convertToAuditEvent);
            case APPLICATION -> (principals.isEmpty() ? applicationAuditEventRepository.findAllWithData(pageable)
                    : applicationAuditEventRepository.findAllWithDataByPrincipalIn(principals, pageable)).map(auditEventConverter::convertToAuditEvent);
        };
    }

    /**
     * Finds a page of audit events in the given log whose date lies in the given range.
     *
     * @param logType   which audit log to read
     * @param principal only the events of this principal (login) are returned, or all events if it is null or blank
     * @param fromDate  the start of the range
     * @param toDate    the end of the range
     * @param pageable  the pagination information
     * @return a page of audit events
     */
    public Page<AuditEvent> findByDates(AuditLogType logType, @Nullable String principal, Instant fromDate, Instant toDate, Pageable pageable) {
        Collection<String> principals = principalSpellings(principal);
        return switch (logType) {
            case GENERAL -> (principals.isEmpty() ? persistenceAuditEventRepository.findAllWithDataByAuditEventDateBetween(fromDate, toDate, pageable)
                    : persistenceAuditEventRepository.findAllWithDataByPrincipalInAndAuditEventDateBetween(principals, fromDate, toDate, pageable))
                    .map(auditEventConverter::convertToAuditEvent);
            case SECURITY -> (principals.isEmpty() ? securityAuditEventRepository.findAllWithDataByAuditEventDateBetween(fromDate, toDate, pageable)
                    : securityAuditEventRepository.findAllWithDataByPrincipalInAndAuditEventDateBetween(principals, fromDate, toDate, pageable))
                    .map(auditEventConverter::convertToAuditEvent);
            case APPLICATION -> (principals.isEmpty() ? applicationAuditEventRepository.findAllWithDataByAuditEventDateBetween(fromDate, toDate, pageable)
                    : applicationAuditEventRepository.findAllWithDataByPrincipalInAndAuditEventDateBetween(principals, fromDate, toDate, pageable))
                    .map(auditEventConverter::convertToAuditEvent);
        };
    }

    /**
     * The spellings of a login to search for. A login is stored in lower case when a user signs in, but a failed attempt keeps the login as it was typed, so the term is
     * searched as the administrator typed it (trimmed) and lower cased. Both are compared for equality. That keeps the search on the index on principal and date; a contains
     * search, or a comparison that lower cases the stored principal, could not use it and would read the whole date range of the largest log.
     *
     * @param principal the term typed by the administrator
     * @return one or two spellings to match, or an empty collection if no principal is searched
     */
    private static Collection<String> principalSpellings(@Nullable String principal) {
        if (principal == null || principal.isBlank()) {
            return List.of();
        }
        String typed = principal.strip();
        String lowerCased = typed.toLowerCase(Locale.ENGLISH);
        return typed.equals(lowerCased) ? List.of(typed) : List.of(typed, lowerCased);
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
