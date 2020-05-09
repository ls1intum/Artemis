package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.service.NotificationService;
import de.tum.in.www1.artemis.service.SystemNotificationService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.PaginationUtil;
import io.github.jhipster.web.util.ResponseUtil;
import io.swagger.annotations.ApiParam;

/**
 * REST controller for managing Notification.
 */
@RestController
@RequestMapping("/api")
public class NotificationResource {

    private final Logger log = LoggerFactory.getLogger(NotificationResource.class);

    private static final String ENTITY_NAME = "notification";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final NotificationRepository notificationRepository;

    private final NotificationService notificationService;

    private final SystemNotificationService systemNotificationService;

    private final UserService userService;

    public NotificationResource(NotificationRepository notificationRepository, NotificationService notificationService, UserService userService,
            SystemNotificationService systemNotificationService) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.systemNotificationService = systemNotificationService;
        this.userService = userService;
    }

    /**
     * POST /notifications : Create a new notification.
     *
     * @param notification the notification to create
     * @return the ResponseEntity with status 201 (Created) and with body the new notification, or with status 400 (Bad Request) if the notification has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/notifications")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Notification> createNotification(@RequestBody Notification notification) throws URISyntaxException {
        log.debug("REST request to save Notification : {}", notification);
        if (notification.getId() != null) {
            throw new BadRequestAlertException("A new notification cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Notification result = notificationRepository.save(notification);
        return ResponseEntity.created(new URI("/api/notifications/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * GET /notifications : get all notifications by pages.
     *
     * @param pageable Pagination information for fetching the nofications
     * @return the list notifications
     */
    @GetMapping("/notifications")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Notification>> getAllNotificationsForCurrentUser(@ApiParam Pageable pageable) {
        User currentUser = userService.getUserWithGroupsAndAuthorities();
        final Page<Notification> page = notificationService.findAllExceptSystem(currentUser, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET /notifications/sidebar : Get all recent notifications (after last read) for the current user and a batch of
     * non-recent notifications if the page number is equal to 0. If the page number is greater than 0 only the
     * corresponding paged batch of non-recent notifications will be returned.
     *
     * @param pageable Pagination information for fetching the notifications
     * @return list of notifications
     */
    @GetMapping("/notifications/sidebar")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Notification>> getNotificationsNEW(@ApiParam Pageable pageable) {
        List<Notification> notifications = new ArrayList<>();
        List<Notification> recentNotifications = new ArrayList<>();
        List<Notification> nonRecentNotifications = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();
        User currentUser = userService.getUserWithGroupsAndAuthorities();
        // Get all notifications whose notification date is before the current user's lastNotificationRead when first page is requested.
        if (pageable.getPageNumber() == 0 && currentUser != null) {
            recentNotifications = notificationService.findAllRecentExceptSystem(currentUser);
            notifications.addAll(recentNotifications);
        }
        // Get batch of notifications whose notification date is after the current user's lastNotificationRead.
        if (currentUser != null) {
            final Page<Notification> page = notificationService.findAllNonRecentExceptSystem(currentUser, pageable);
            headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
            nonRecentNotifications = page.getContent();
            notifications.addAll(nonRecentNotifications);
        }
        // Overwrite header `X-Total-Count` so that recent and non-recent notifications are included.
        headers.set("X-Total-Count", String.valueOf(recentNotifications.size() + nonRecentNotifications.size()));
        return new ResponseEntity<>(notifications, headers, HttpStatus.OK);
    }

    /**
     * PUT /notifications : Updates an existing notification.
     *
     * @param notification the notification to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated notification, or with status 400 (Bad Request) if the notification is not valid, or with status 500
     *         (Internal Server Error) if the notification couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/notifications")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Notification> updateNotification(@RequestBody Notification notification) throws URISyntaxException {
        log.debug("REST request to update Notification : {}", notification);
        if (notification.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        Notification result = notificationRepository.save(notification);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, notification.getId().toString())).body(result);
    }

    /**
     * GET /notifications/:id : get the "id" notification.
     *
     * @param id the id of the notification to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the notification, or with status 404 (Not Found)
     */
    @GetMapping("/notifications/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Notification> getNotification(@PathVariable Long id) {
        log.debug("REST request to get Notification : {}", id);
        Optional<Notification> notification = notificationRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(notification);
    }

    /**
     * DELETE /notifications/:id : delete the "id" notification.
     *
     * @param id the id of the notification to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/notifications/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        log.debug("REST request to delete Notification : {}", id);
        notificationRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
