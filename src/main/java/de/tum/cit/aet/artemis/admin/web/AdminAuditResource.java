package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.web.util.PaginationUtil.generatePaginationHttpHeaders;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.admin.service.AuditEventService;
import de.tum.cit.aet.artemis.core.config.audit.AuditLogType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.web.util.ResponseUtil;

/**
 * REST controller for getting the audit events.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage("monitoring/audit-log")
@RestController
@SuppressWarnings("deprecation")
@RequestMapping("api/admin/")
public class AdminAuditResource {

    private final AuditEventService auditEventService;

    public AdminAuditResource(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    /**
     * GET /audits : get a page of AuditEvents from one of the three audit logs.
     *
     * @param logType  which audit log to read; defaults to the general (authentication) log so existing callers keep working
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of AuditEvents in body
     */
    @GetMapping("audits")
    public ResponseEntity<List<AuditEvent>> getAll(@RequestParam(value = "logType", defaultValue = "GENERAL") AuditLogType logType, Pageable pageable) {
        Page<AuditEvent> page = auditEventService.findAll(logType, pageable);
        HttpHeaders headers = generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /audits : get a page of AuditEvents between the fromDate and toDate from one of the three audit logs.
     *
     * @param fromDate the start of the time period of AuditEvents to get
     * @param toDate   the end of the time period of AuditEvents to get
     * @param logType  which audit log to read; defaults to the general (authentication) log so existing callers keep working
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of AuditEvents in body
     */
    @GetMapping(value = "audits", params = { "fromDate", "toDate" })
    public ResponseEntity<List<AuditEvent>> getByDates(@RequestParam(value = "fromDate") LocalDate fromDate, @RequestParam(value = "toDate") LocalDate toDate,
            @RequestParam(value = "logType", defaultValue = "GENERAL") AuditLogType logType, Pageable pageable) {

        Instant from = fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = toDate.atStartOfDay(ZoneId.systemDefault()).plusDays(1).toInstant();

        Page<AuditEvent> page = auditEventService.findByDates(logType, from, to, pageable);
        HttpHeaders headers = generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /audits/:id : get an AuditEvent by id from one of the three audit logs.
     *
     * @param id      the id of the entity to get
     * @param logType which audit log to read; ids are only unique within a log
     * @return the ResponseEntity with status 200 (OK) and the AuditEvent in body, or status 404 (Not Found)
     */
    @GetMapping("audits/{id:.+}")
    public ResponseEntity<AuditEvent> get(@PathVariable Long id, @RequestParam(value = "logType", defaultValue = "GENERAL") AuditLogType logType) {
        return ResponseUtil.wrapOrNotFound(auditEventService.find(logType, id));
    }
}
