package de.tum.in.www1.artemis.web.rest;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing NotificationSettings (NotificationSettings).
 */
@RestController
@RequestMapping("api/")
public class NotificationSettingsResource {

    private final Logger log = LoggerFactory.getLogger(NotificationSettingsResource.class);

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
     *
     * Fetches the NotificationSettings for the current user from the server.
     * If the user has not yet modified the settings there will be none in the database, then
     *
     * @return the list of found NotificationSettings
     */
    @GetMapping("notification-settings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<NotificationSetting>> getNotificationSettingsForCurrentUser() {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all NotificationSettings for current user {}", currentUser);
        Set<NotificationSetting> notificationSettingSet = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(currentUser.getId());
        notificationSettingSet = notificationSettingsService.checkLoadedNotificationSettingsForCorrectness(notificationSettingSet, currentUser);
        return new ResponseEntity<>(notificationSettingSet, HttpStatus.OK);
    }

    /**
     * PUT notification-settings : Save NotificationSettings for current user
     *
     * Saves the provided NotificationSettings to the server.
     * @param notificationSettings which should be saved to the notificationSetting database.
     *
     * @return the NotificationSettings that just got saved for the current user as array
     * 200 for a successful execution, 400 if the user provided empty settings to save, 500 if the save call returns empty settings
     */
    @PutMapping("notification-settings")
    @PreAuthorize("hasRole('USER')")
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
        NotificationSetting[] resultAsArray = resultAsList.toArray(new NotificationSetting[0]);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "notificationSetting", "test")).body(resultAsArray);
    }
}
