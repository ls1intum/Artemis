package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

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

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.SystemNotification;
import de.tum.in.www1.artemis.domain.notification.UserOption;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
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

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public NotificationResource(NotificationRepository notificationRepository, UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * POST /notifications : Create a new notification.
     *
     * @param notification the notification to create
     * @return the ResponseEntity with status 201 (Created) and with body the new notification, or with status 400 (Bad Request) if the notification has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/notifications")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Notification> createNotification(@RequestBody Notification notification) throws URISyntaxException {
        log.debug("REST request to save Notification : {}", notification);
        if (notification.getId() != null) {
            throw new BadRequestAlertException("A new notification cannot already have an ID", ENTITY_NAME, "idexists");
        }
        restrictSystemNotificationsToAdmin(null, notification);
        Notification result = notificationRepository.save(notification);
        return ResponseEntity.created(new URI("/api/notifications/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * GET /notifications : Get all notifications by pages.
     *
     * @param pageable Pagination information for fetching the notifications
     * @return the list notifications
     */
    @GetMapping("/notifications")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Notification>> getAllNotificationsForCurrentUser(@ApiParam Pageable pageable) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        final Page<Notification> page = notificationRepository.findAllNotificationsForRecipientWithLogin(currentUser.getGroups(), currentUser.getLogin(), pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/notifications/fetch-options")
    public ResponseEntity<List<UserOption>> getNotificationOptionsForCurrentUser(@ApiParam Pageable pageable) {
        log.info("!!!IAMSAY: [NotificationResource] getNotificationOptionsForCurrentUser");
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.info("!!!IAMSAY: [NotificationResource] : currentUser = " + currentUser);
        // final Page<NotificationOption> page = notificationRepository.findAllNotificationOptionsForRecipientWithId(currentUser.getId(), pageable);
        final Page<UserOption> page = notificationRepository.findAllUserOptionsForRecipientWithId(currentUser.getId(), pageable);
        log.info("!!!IAMSAY: [NotificationResource] : page = " + page);
        log.info("!!!IAMSAY: [NotificationResource] : page.content = " + page.getContent());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
    /*
     * @PostMapping("/save-options") public ResponseEntity<NotificationOption[]> saveNotificationOptionsForCurrentUser(@RequestBody NotificationOption[] options) { // return
     * badRequest(); if (options == null) { return conflict(); } User currentUser = userRepository.getUserWithGroupsAndAuthorities(); Long currentUserId = currentUser.getId(); for
     * (NotificationOption option : options) { if (option.getUser_id().getId() != currentUserId) { // TODO try to rework User_id to actually be only the id return conflict(); } }
     * // TODO maybe more checks // NotificationOption[] savedOptions = notificationRepository.saveAllNotificationOptionsForRecipientWithId(currentUserId, options);
     * notificationRepository.saveAllNotificationOptionsForRecipientWithId(currentUserId, options); return ok(); }
     */

    /**
     * PUT /notifications : Updates an existing notification.
     *
     * @param notification the notification to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated notification, or with status 400 (Bad Request) if the notification is not valid, or with status 500
     *         (Internal Server Error) if the notification couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/notifications")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Notification> updateNotification(@RequestBody Notification notification) throws URISyntaxException {
        log.debug("REST request to update Notification : {}", notification);
        if (notification.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        restrictSystemNotificationsToAdmin(null, notification);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        log.debug("REST request to delete Notification : {}", id);
        restrictSystemNotificationsToAdmin(id, null);
        notificationRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }

    private void restrictSystemNotificationsToAdmin(Long notificationId, Notification notification) {
        Long id = notificationId != null ? notificationId : notification.getId();
        Notification subjectOfChange = id == null ? notification : notificationRepository.findById(id).orElse(null);
        if (subjectOfChange instanceof SystemNotification && !authorizationCheckService.isAdmin()) {
            throw new AccessForbiddenException("System notifications can only be managed by admins");
        }
    }
}
