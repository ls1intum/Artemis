package de.tum.in.www1.artemis.web.rest.admin;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.SystemNotification;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.SystemNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for administrating system notifications.
 */
@RestController
@RequestMapping("api/admin/")
public class AdminSystemNotificationResource {

    private final Logger log = LoggerFactory.getLogger(AdminSystemNotificationResource.class);

    private static final String ENTITY_NAME = "systemNotification";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SystemNotificationRepository systemNotificationRepository;

    private final SystemNotificationService systemNotificationService;

    public AdminSystemNotificationResource(SystemNotificationRepository systemNotificationRepository, SystemNotificationService systemNotificationService) {
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
    @PostMapping("system-notifications")
    @EnforceAdmin
    public ResponseEntity<Notification> createSystemNotification(@RequestBody SystemNotification systemNotification) throws URISyntaxException {
        log.debug("REST request to save SystemNotification : {}", systemNotification);
        if (systemNotification.getId() != null) {
            throw new BadRequestAlertException("A new system notification cannot already have an ID", ENTITY_NAME, "idExists");
        }
        this.systemNotificationService.validateDatesElseThrow(systemNotification);
        SystemNotification result = systemNotificationRepository.save(systemNotification);
        systemNotificationService.distributeActiveAndFutureNotificationsToClients();
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
    @PutMapping("system-notifications")
    @EnforceAdmin
    public ResponseEntity<SystemNotification> updateSystemNotification(@RequestBody SystemNotification systemNotification) {
        log.debug("REST request to update SystemNotification : {}", systemNotification);
        if (systemNotification.getId() == null) {
            throw new BadRequestAlertException("ID must not be null", ENTITY_NAME, "idNull");
        }
        this.systemNotificationService.validateDatesElseThrow(systemNotification);
        if (!systemNotificationRepository.existsById(systemNotification.getId())) {
            throw new BadRequestAlertException("No system notification with this ID found", ENTITY_NAME, "idNull");
        }
        SystemNotification result = systemNotificationRepository.save(systemNotification);
        systemNotificationService.distributeActiveAndFutureNotificationsToClients();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, systemNotification.getId().toString())).body(result);
    }

    /**
     * DELETE /system-notifications/:id : delete the "id" system notification.
     *
     * @param id the id of the system notification to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("system-notifications/{id}")
    @EnforceAdmin
    public ResponseEntity<Void> deleteSystemNotification(@PathVariable Long id) {
        log.debug("REST request to delete SystemNotification : {}", id);
        systemNotificationRepository.deleteById(id);
        systemNotificationService.distributeActiveAndFutureNotificationsToClients();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
