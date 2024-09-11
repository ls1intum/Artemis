package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.repository.SystemNotificationRepository;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing SystemNotification.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class SystemNotificationResource {

    private static final Logger log = LoggerFactory.getLogger(SystemNotificationResource.class);

    private final SystemNotificationRepository systemNotificationRepository;

    public SystemNotificationResource(SystemNotificationRepository systemNotificationRepository) {
        this.systemNotificationRepository = systemNotificationRepository;
    }

    /**
     * GET /system-notifications : get all system notifications for administration purposes.
     *
     * @param pageable instance of the pageable interface to enable paging
     * @return the list of system notifications
     */
    @GetMapping("system-notifications")
    @EnforceAtLeastTutor
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
    @EnforceAtLeastEditor
    public ResponseEntity<SystemNotification> getSystemNotification(@PathVariable Long notificationId) {
        log.debug("REST request to get SystemNotification : {}", notificationId);
        Optional<SystemNotification> systemNotification = systemNotificationRepository.findById(notificationId);
        return ResponseUtil.wrapOrNotFound(systemNotification);
    }
}
