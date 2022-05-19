package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.SystemNotification;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;
import de.tum.in.www1.artemis.service.SystemNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/** REST controller for managing SystemNotification. */
@RestController
@RequestMapping("/api")
public class SystemNotificationResource {

    private final Logger log = LoggerFactory.getLogger(SystemNotificationResource.class);

    private static final String ENTITY_NAME = "systemNotification";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SystemNotificationRepository systemNotificationRepository;

    private final SystemNotificationService systemNotificationService;

    public SystemNotificationResource(SystemNotificationRepository systemNotificationRepository, SystemNotificationService systemNotificationService) {
        this.systemNotificationRepository = systemNotificationRepository;
        this.systemNotificationService = systemNotificationService;
    }

    /**
     * POST /system-notifications : Create a new system notification.
     *
     * @param systemNotification the system notification to create
     * @return the ResponseEntity with status 201 (Created) and with body the new system notification, or with status 400 (Bad Request) if the system notification has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/system-notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Notification> createSystemNotification(@RequestBody SystemNotification systemNotification) throws URISyntaxException {
        log.debug("REST request to save SystemNotification : {}", systemNotification);
        if (systemNotification.getId() != null) {
            throw new BadRequestAlertException("A new system notification cannot already have an ID", ENTITY_NAME, "idexists");
        }
        SystemNotification result = systemNotificationRepository.save(systemNotification);
        systemNotificationService.sendNotification(systemNotification);
        return ResponseEntity.created(new URI("/api/notifications/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /system-notifications : Updates an existing system notification.
     *
     * @param systemNotification the system notification to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated notification, or with status 400 (Bad Request) if the system notification is not valid, or with
     *         status 500 (Internal Server Error) if the system notification couldn't be updated
     */
    @PutMapping("/system-notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemNotification> updateSystemNotification(@RequestBody SystemNotification systemNotification) {
        log.debug("REST request to update SystemNotification : {}", systemNotification);
        if (systemNotification.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        SystemNotification result = systemNotificationRepository.save(systemNotification);
        systemNotificationService.sendNotification(result);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, systemNotification.getId().toString())).body(result);
    }

    /**
     * GET /system-notifications : get all system notifications for administration purposes.
     *
     * @param pageable instance of the pageable interface to enable paging
     * @return the list of system notifications
     */
    @GetMapping("/system-notifications")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<SystemNotification>> getAllSystemNotifications(@ApiParam Pageable pageable) {
        log.debug("REST request to get all Courses the user has access to");
        final Page<SystemNotification> page = systemNotificationRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /system-notifications/:id : get the "id" system notification.
     *
     * @param id the id of the system notification to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the notification, or with status 404 (Not Found)
     */
    @GetMapping("/system-notifications/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SystemNotification> getSystemNotification(@PathVariable Long id) {
        log.debug("REST request to get SystemNotification : {}", id);
        Optional<SystemNotification> systemNotification = systemNotificationRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(systemNotification);
    }

    /**
     * DELETE /system-notifications/:id : delete the "id" system notification.
     *
     * @param id the id of the system notification to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/system-notifications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSystemNotification(@PathVariable Long id) {
        log.debug("REST request to delete SystemNotification : {}", id);
        systemNotificationRepository.deleteById(id);
        systemNotificationService.sendNotification(null);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }

    /**
     * GET /system-notifications/:id : get the "id" system notification.
     * This route is also accessible for unauthenticated users.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the notification, or with status 404 (Not Found)
     */
    @GetMapping("/system-notifications/active-notification")
    public SystemNotification getActiveSystemNotification() {
        log.debug("REST request to get active SystemNotification");
        return systemNotificationService.findActiveSystemNotification();
    }
}
