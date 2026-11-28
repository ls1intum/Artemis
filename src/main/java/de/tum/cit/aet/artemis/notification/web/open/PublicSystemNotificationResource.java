package de.tum.cit.aet.artemis.notification.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.notification.dto.SystemNotificationDTO;
import de.tum.cit.aet.artemis.notification.service.SystemNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * REST controller for public system notifications.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.SYSTEM_NOTIFICATIONS)
@RestController
@RequestMapping("api/notification/public/")
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
     * @return status 200 (OK) with the active and future notifications, including an empty list when none exist
     */
    @Operation(operationId = "getActiveAndFutureSystemNotifications")
    @ApiResponse(responseCode = "200", description = "Active and future system notifications, accessible anonymously")
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @GetMapping("system-notifications/active")
    @EnforceNothing
    public ResponseEntity<List<SystemNotificationDTO>> getActiveAndFutureSystemNotifications() {
        log.debug("REST request to get relevant system notifications");
        return ResponseEntity.ok(systemNotificationService.findAllActiveAndFutureSystemNotificationDTOs());
    }
}
