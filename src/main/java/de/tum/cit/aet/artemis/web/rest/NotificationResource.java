package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.MESSAGE_REPLY_IN_CONVERSATION_TITLE;
import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.NEW_MESSAGE_TITLE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.communication.domain.notification.Notification;
import de.tum.cit.aet.artemis.communication.repository.NotificationRepository;
import de.tum.cit.aet.artemis.communication.repository.NotificationSettingRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.service.notifications.NotificationSettingsCommunicationChannel;
import de.tum.cit.aet.artemis.service.notifications.NotificationSettingsService;
import de.tum.cit.aet.artemis.service.tutorialgroups.TutorialGroupService;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Notification.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class NotificationResource {

    private static final Logger log = LoggerFactory.getLogger(NotificationResource.class);

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
        long start = System.nanoTime();
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.info("REST request to get notifications page {} with size {} for current user {} filtered by settings", pageable.getPageNumber(), pageable.getPageSize(),
                currentUser.getLogin());
        var tutorialGroupIds = tutorialGroupService.findAllForNotifications(currentUser);
        var notificationSettings = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(currentUser.getId());
        var deactivatedTypes = notificationSettingsService.findDeactivatedNotificationTypes(NotificationSettingsCommunicationChannel.WEBAPP, notificationSettings);
        var deactivatedTitles = notificationSettingsService.convertNotificationTypesToTitles(deactivatedTypes);
        // Note: at the moment, we only support to show notifications from the last month, because without a proper date the query below becomes too slow
        var hideNotificationsUntilDate = currentUser.getHideNotificationsUntil();
        var notificationDateLimit = ZonedDateTime.now().minusMonths(1);
        if (hideNotificationsUntilDate == null || hideNotificationsUntilDate.isBefore(notificationDateLimit)) {
            hideNotificationsUntilDate = notificationDateLimit;
        }
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
        log.info("Load notifications for user {} done in {}", currentUser.getLogin(), TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}
