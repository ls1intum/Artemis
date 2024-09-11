package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.NotificationSetting;
import de.tum.cit.aet.artemis.communication.repository.NotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.NotificationSettingsService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing NotificationSettings (NotificationSettings).
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class NotificationSettingsResource {

    private static final Logger log = LoggerFactory.getLogger(NotificationSettingsResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final NotificationSettingRepository notificationSettingRepository;

    private final UserRepository userRepository;

    private final NotificationSettingsService notificationSettingsService;

    public NotificationSettingsResource(NotificationSettingRepository notificationSettingRepository, UserRepository userRepository,
            NotificationSettingsService notificationSettingsService) {
        this.notificationSettingRepository = notificationSettingRepository;
        this.userRepository = userRepository;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * GET notification-settings : Get all NotificationSettings for current user
     * <p>
     * Fetches the NotificationSettings for the current user from the server.
     * If the user has not yet modified the settings there will be none in the database, then
     *
     * @return the list of found NotificationSettings
     */
    @GetMapping("notification-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<NotificationSetting>> getNotificationSettingsForCurrentUser() {
        long start = System.nanoTime();
        User currentUser = userRepository.getUser();
        log.debug("REST request to get all NotificationSettings for current user {}", currentUser);
        Set<NotificationSetting> notificationSettingSet = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(currentUser.getId());
        notificationSettingSet = notificationSettingsService.checkLoadedNotificationSettingsForCorrectness(notificationSettingSet, currentUser);
        log.info("Load notification settings for current user done in {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(notificationSettingSet, HttpStatus.OK);
    }

    /**
     * PUT notification-settings : Save NotificationSettings for current user
     * <p>
     * Saves the provided NotificationSettings to the server.
     *
     * @param notificationSettings which should be saved to the notificationSetting database.
     * @return the NotificationSettings that just got saved for the current user as array
     *         200 for a successful execution, 400 if the user provided empty settings to save, 500 if the save call returns empty settings
     */
    @PutMapping("notification-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<NotificationSetting[]> saveNotificationSettingsForCurrentUser(@NotNull @RequestBody NotificationSetting[] notificationSettings) {
        if (notificationSettings.length == 0) {
            throw new BadRequestAlertException("Cannot save non-existing Notification Settings", "NotificationSettings", "notificationSettingsEmpty");
        }
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to save NotificationSettings : {} for current user {}", notificationSettings, currentUser);
        notificationSettingsService.setCurrentUser(notificationSettings, currentUser);
        List<NotificationSetting> resultAsList = notificationSettingRepository.saveAll(Arrays.stream(notificationSettings).toList());
        if (resultAsList.isEmpty()) {
            throw new BadRequestAlertException("Error occurred during saving of Notification Settings", "NotificationSettings", "notificationSettingsEmptyAfterSave");
        }
        NotificationSetting[] resultAsArray = resultAsList.toArray(NotificationSetting[]::new);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "notificationSetting", "test")).body(resultAsArray);
    }

    /**
     * GET muted-conversations : Loads the list of all muted conversations of a user
     *
     * @return ResponseEntity with status 200 and the list of muted conversations
     */
    @GetMapping("muted-conversations")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<Long>> getMutedConversations() {
        User user = userRepository.getUser();
        Set<Long> mutedConversations = notificationSettingRepository.findMutedConversations(user.getId());
        return ResponseEntity.ok(mutedConversations);
    }
}
