package de.tum.cit.aet.artemis.web.rest.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.domain.notification.SystemNotification;
import de.tum.cit.aet.artemis.service.SystemNotificationService;

/**
 * REST controller for public system notifications.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/public/")
public class PublicSystemNotificationResource {

    private static final Logger log = LoggerFactory.getLogger(PublicSystemNotificationResource.class);

    private final SystemNotificationService systemNotificationService;

    public PublicSystemNotificationResource(SystemNotificationService systemNotificationService) {
        this.systemNotificationService = systemNotificationService;
    }

    /**
     * Returns all system notifications with an expiry date in the future or no expiry date.
     * This route is also accessible for unauthenticated users.
     *
     * @return the ResponseEntity with status 200 (OK) and with body the notification, or with status 404 (Not Found)
     */
    @GetMapping("system-notifications/active")
    @EnforceNothing
    public ResponseEntity<List<SystemNotification>> getActiveAndFutureSystemNotifications() {
        log.debug("REST request to get relevant system notifications");
        return ResponseEntity.ok(systemNotificationService.findAllActiveAndFutureSystemNotifications());
    }
}
