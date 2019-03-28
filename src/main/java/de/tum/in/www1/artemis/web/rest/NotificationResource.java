package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.service.NotificationService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import io.swagger.annotations.ApiParam;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** REST controller for managing Notification. */
@RestController
@RequestMapping("/api")
public class NotificationResource {

    private final Logger log = LoggerFactory.getLogger(NotificationResource.class);

    private static final String ENTITY_NAME = "notification";

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationResource(
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            UserService userService) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    /**
    * POST /notifications : Create a new notification.
    *
    * @param notification the notification to create
    * @return the ResponseEntity with status 201 (Created) and with body the new notification, or
    *     with status 400 (Bad Request) if the notification has already an ID
    * @throws URISyntaxException if the Location URI syntax is incorrect
    */
    @PostMapping("/notifications")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Notification> createNotification(@RequestBody Notification notification)
            throws URISyntaxException {
        log.debug("REST request to save Notification : {}", notification);
        if (notification.getId() != null) {
            throw new BadRequestAlertException(
                    "A new notification cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Notification result = notificationRepository.save(notification);
        return ResponseEntity.created(new URI("/api/notifications/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
                .body(result);
    }

    /**
    * GET /system-notifications : get all system notifications for administration purposes.
    *
    * @return the list of system notifications
    */
    @GetMapping("/notifications/for-user")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Notification>> getAllSystemNotifications(@ApiParam Pageable pageable) {
        log.debug("REST request to get all Courses the user has access to");
        User currentUser = userService.getUserWithGroupsAndAuthorities();

        final List<Notification> page = notificationService.findAllExceptSystem(currentUser);
        return new ResponseEntity<>(page, null, HttpStatus.OK);
    }

    /**
    * PUT /notifications : Updates an existing notification.
    *
    * @param notification the notification to update
    * @return the ResponseEntity with status 200 (OK) and with body the updated notification, or with
    *     status 400 (Bad Request) if the notification is not valid, or with status 500 (Internal
    *     Server Error) if the notification couldn't be updated
    * @throws URISyntaxException if the Location URI syntax is incorrect
    */
    @PutMapping("/notifications")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Notification> updateNotification(@RequestBody Notification notification)
            throws URISyntaxException {
        log.debug("REST request to update Notification : {}", notification);
        if (notification.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        Notification result = notificationRepository.save(notification);
        return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, notification.getId().toString()))
                .body(result);
    }

    /**
    * GET /notifications/:id : get the "id" notification.
    *
    * @param id the id of the notification to retrieve
    * @return the ResponseEntity with status 200 (OK) and with body the notification, or with status
    *     404 (Not Found)
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
        return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))
                .build();
    }
}
