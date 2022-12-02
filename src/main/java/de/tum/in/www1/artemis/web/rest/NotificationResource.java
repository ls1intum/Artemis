package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

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
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsService;
import io.swagger.v3.oas.annotations.Parameter;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Notification.
 */
@RestController
public class NotificationResource {

    private final Logger log = LoggerFactory.getLogger(NotificationResource.class);

    private static final String ENTITY_NAME = "notification";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final NotificationRepository notificationRepository;

    private final UserRepository userRepository;

    private final NotificationSettingRepository notificationSettingRepository;

    private final NotificationSettingsService notificationSettingsService;

    public NotificationResource(NotificationRepository notificationRepository, UserRepository userRepository, NotificationSettingRepository notificationSettingRepository,
            AuthorizationCheckService authorizationCheckService, NotificationSettingsService notificationSettingsService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * GET notifications : Get all notifications that also align with the current user notification settings by pages.
     *
     * @param pageable Pagination information for fetching the notifications
     * @return the filtered list of notifications based on current user settings
     */
    @GetMapping("notifications")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Notification>> getAllNotificationsForCurrentUserFilteredBySettings(@Parameter Pageable pageable) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all Notifications for current user {} filtered by settings", user);
        var notificationSettings = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(user.getId());
        var deactivatedTypes = notificationSettingsService.findDeactivatedNotificationTypes(NotificationSettingsCommunicationChannel.WEBAPP, notificationSettings);
        Set<String> deactivatedTitles = notificationSettingsService.convertNotificationTypesToTitles(deactivatedTypes);
        final ZonedDateTime hideNotificationsUntilDate = user.getHideNotificationsUntil();
        final Page<Notification> page;
        if (deactivatedTitles.isEmpty()) {
            page = notificationRepository.findAllNotificationsForRecipientWithLogin(user.getGroups(), user.getLogin(), hideNotificationsUntilDate, pageable);
        }
        else {
            page = notificationRepository.findAllNotificationsFilteredBySettingsForRecipientWithLogin(user.getGroups(), user.getLogin(), hideNotificationsUntilDate,
                    deactivatedTitles, pageable);
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}
