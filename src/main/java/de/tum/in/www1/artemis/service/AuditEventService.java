package de.tum.in.www1.artemis.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.config.audit.AuditEventConverter;
import de.tum.in.www1.artemis.repository.PersistenceAuditEventRepository;
import io.github.jhipster.config.JHipsterProperties;

/**
 * Service for managing audit events.
 * <p>
 * This is the default implementation to support SpringBoot Actuator AuditEventRepository
 */
@Service
@Transactional
public class AuditEventService {

    private final Logger log = LoggerFactory.getLogger(AuditEventService.class);

    private final JHipsterProperties jHipsterProperties;

    private final PersistenceAuditEventRepository persistenceAuditEventRepository;

    private final AuditEventConverter auditEventConverter;

    public AuditEventService(PersistenceAuditEventRepository persistenceAuditEventRepository, AuditEventConverter auditEventConverter, JHipsterProperties jhipsterProperties) {

        this.persistenceAuditEventRepository = persistenceAuditEventRepository;
        this.auditEventConverter = auditEventConverter;
        this.jHipsterProperties = jhipsterProperties;
    }

    /**
     * Old audit events should be automatically deleted after 30 days.
     *
     * This is scheduled to get fired at 12:00 (am).
     */
    @Scheduled(cron = "0 0 12 * * ?")
    public void removeOldAuditEvents() {
        persistenceAuditEventRepository.findByAuditEventDateBefore(Instant.now().minus(jHipsterProperties.getAuditEvents().getRetentionPeriod(), ChronoUnit.DAYS))
                .forEach(auditEvent -> {
                    log.debug("Deleting audit data {}", auditEvent.toString());
                    persistenceAuditEventRepository.delete(auditEvent);
                });
    }

    public Page<AuditEvent> findAll(Pageable pageable) {
        return persistenceAuditEventRepository.findAll(pageable).map(auditEventConverter::convertToAuditEvent);
    }

    public Page<AuditEvent> findByDates(Instant fromDate, Instant toDate, Pageable pageable) {
        return persistenceAuditEventRepository.findAllByAuditEventDateBetween(fromDate, toDate, pageable).map(auditEventConverter::convertToAuditEvent);
    }

    public Optional<AuditEvent> find(Long id) {
        return Optional.ofNullable(persistenceAuditEventRepository.findById(id)).filter(Optional::isPresent).map(Optional::get).map(auditEventConverter::convertToAuditEvent);
    }
}
