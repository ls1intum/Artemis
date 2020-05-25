package de.tum.in.www1.artemis.web.rest;

import java.util.List;

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
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.PaginationUtil;
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

    private final UserService userService;

    public NotificationResource(NotificationRepository notificationRepository, NotificationService notificationService, UserService userService) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    /**
     * GET /notifications : Get all notifications by pages.
     *
     * @param pageable Pagination information for fetching the notifications
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
