package de.tum.in.www1.artemis.web.rest.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.AuditEventService;
import io.swagger.v3.oas.annotations.Parameter;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for getting the audit events.
 */
@RestController
@RequestMapping("admin/")
public class AuditResource {

    private final AuditEventService auditEventService;

    public AuditResource(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    /**
     * GET /audits : get a page of AuditEvents.
     *
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of AuditEvents in body
     */
    @GetMapping("audits")
    @EnforceAdmin
    public ResponseEntity<List<AuditEvent>> getAll(@Parameter Pageable pageable) {
        Page<AuditEvent> page = auditEventService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /audits : get a page of AuditEvents between the fromDate and toDate.
     *
     * @param fromDate the start of the time period of AuditEvents to get
     * @param toDate   the end of the time period of AuditEvents to get
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of AuditEvents in body
     */
    @GetMapping(value = ("audits"), params = { "fromDate", "toDate" })
    @EnforceAdmin
    public ResponseEntity<List<AuditEvent>> getByDates(@RequestParam(value = "fromDate") LocalDate fromDate, @RequestParam(value = "toDate") LocalDate toDate,
            @Parameter Pageable pageable) {

        Instant from = fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = toDate.atStartOfDay(ZoneId.systemDefault()).plusDays(1).toInstant();

        Page<AuditEvent> page = auditEventService.findByDates(from, to, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}
