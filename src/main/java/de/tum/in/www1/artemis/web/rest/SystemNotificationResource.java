package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.notification.SystemNotification;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;
import de.tum.in.www1.artemis.service.SystemNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;

/** REST controller for managing SystemNotification. */
@RestController
@RequestMapping("api")
public class SystemNotificationResource {

    private final Logger log = LoggerFactory.getLogger(SystemNotificationResource.class);

    private final SystemNotificationRepository systemNotificationRepository;

    private final SystemNotificationService systemNotificationService;

    public SystemNotificationResource(SystemNotificationRepository systemNotificationRepository, SystemNotificationService systemNotificationService) {
        this.systemNotificationRepository = systemNotificationRepository;
        this.systemNotificationService = systemNotificationService;
    }

    /**
     * GET /system-notifications : get all system notifications for administration purposes.
     *
     * @param pageable instance of the pageable interface to enable paging
     * @return the list of system notifications
     */
    @GetMapping("system-notifications")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<SystemNotification>> getAllSystemNotifications(@ApiParam Pageable pageable) {
        log.debug("REST request to get all Courses the user has access to");
        final Page<SystemNotification> page = systemNotificationRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /system-notifications/:notificationId : get the "id" system notification.
     *
     * @param notificationId the id of the system notification to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the notification, or with status 404 (Not Found)
     */
    @GetMapping("system-notifications/{notificationId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SystemNotification> getSystemNotification(@PathVariable Long notificationId) {
        log.debug("REST request to get SystemNotification : {}", notificationId);
        var systemNotification = systemNotificationRepository.findById(notificationId).orElseThrow(() -> new EntityNotFoundException("System Notification", notificationId));
        return ResponseEntity.ok().body(systemNotification);
    }

    /**
     * Returns all system notifications with an expiry date in the future or no expiry date.
     * This route is also accessible for unauthenticated users.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the notification, or with status 404 (Not Found)
     */
    @GetMapping("system-notifications/active")
    public List<SystemNotification> getActiveAndFutureSystemNotifications() {
        log.debug("REST request to get relevant system notifications");
        return systemNotificationService.findAllActiveAndFutureSystemNotifications();
    }
}
