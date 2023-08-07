package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.MESSAGE_REPLY_IN_CONVERSATION_TITLE;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.NEW_MESSAGE_TITLE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsService;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupService;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Notification.
 */
@RestController
@RequestMapping("api/")
public class NotificationResource {

    private final Logger log = LoggerFactory.getLogger(NotificationResource.class);

    private static final Set<String> TITLES_TO_NOT_LOAD_NOTIFICATION = Set.of(NEW_MESSAGE_TITLE, MESSAGE_REPLY_IN_CONVERSATION_TITLE);

    private final NotificationRepository notificationRepository;

    private final UserRepository userRepository;

    private final NotificationSettingRepository notificationSettingRepository;

    private final NotificationSettingsService notificationSettingsService;

    private final TutorialGroupService tutorialGroupService;

    public NotificationResource(NotificationRepository notificationRepository, UserRepository userRepository, NotificationSettingRepository notificationSettingRepository,
            NotificationSettingsService notificationSettingsService, TutorialGroupService tutorialGroupService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.notificationSettingsService = notificationSettingsService;
        this.tutorialGroupService = tutorialGroupService;
    }

    /**
     * GET notifications : Get all notifications that also align with the current user notification settings by pages.
     *
     * @param pageable Pagination information for fetching the notifications
     * @return the filtered list of notifications based on current user settings
     */
    @GetMapping("notifications")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Notification>> getAllNotificationsForCurrentUserFilteredBySettings(@ApiParam Pageable pageable) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        var tutorialGroupIds = tutorialGroupService.findAllForNotifications(currentUser).stream().map(DomainObject::getId).collect(Collectors.toSet());
        log.debug("REST request to get all Notifications for current user {} filtered by settings", currentUser);
        Set<NotificationSetting> notificationSettings = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(currentUser.getId());
        Set<NotificationType> deactivatedTypes = notificationSettingsService.findDeactivatedNotificationTypes(NotificationSettingsCommunicationChannel.WEBAPP,
                notificationSettings);
        Set<String> deactivatedTitles = notificationSettingsService.convertNotificationTypesToTitles(deactivatedTypes);
        final ZonedDateTime hideNotificationsUntilDate = currentUser.getHideNotificationsUntil();
        final Page<Notification> page;
        if (deactivatedTitles.isEmpty()) {
            page = notificationRepository.findAllNotificationsForRecipientWithLogin(currentUser.getGroups(), currentUser.getLogin(), hideNotificationsUntilDate, tutorialGroupIds,
                    TITLES_TO_NOT_LOAD_NOTIFICATION, pageable);
        }
        else {
            page = notificationRepository.findAllNotificationsFilteredBySettingsForRecipientWithLogin(currentUser.getGroups(), currentUser.getLogin(), hideNotificationsUntilDate,
                    deactivatedTitles, tutorialGroupIds, TITLES_TO_NOT_LOAD_NOTIFICATION, pageable);
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}
