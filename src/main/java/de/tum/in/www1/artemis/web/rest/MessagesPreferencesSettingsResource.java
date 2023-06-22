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

import de.tum.in.www1.artemis.domain.MessagesPreferencesSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.MessagesPreferencesSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.MessagesPreferencesSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing MessagesPreferencesSettings (MessagesPreferencesSettings).
 */
@RestController
@RequestMapping("api/")
public class MessagesPreferencesSettingsResource {

    private final Logger log = LoggerFactory.getLogger(MessagesPreferencesSettingsResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MessagesPreferencesSettingRepository messagesPreferencesSettingRepository;

    private final UserRepository userRepository;

    private final MessagesPreferencesSettingsService messagesPreferencesSettingsService;

    public MessagesPreferencesSettingsResource(MessagesPreferencesSettingsService messagesPreferencesSettingsService, UserRepository userRepository,
            MessagesPreferencesSettingRepository messagesPreferencesSettingRepository) {
        this.messagesPreferencesSettingRepository = messagesPreferencesSettingRepository;
        this.userRepository = userRepository;
        this.messagesPreferencesSettingsService = messagesPreferencesSettingsService;
    }

    /**
     * GET messages-preferences-settings : Get all MessagesPreferencesSettings for current user
     *
     * Fetches the MessagesPreferencesSettings for the current user from the server.
     * If the user has not yet modified the settings there will be none in the database, then
     *
     * @return the list of found MessagesPreferencesSetting
     */
    @GetMapping("messages-preferences-settings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<MessagesPreferencesSetting>> getMessagesPreferencesSettingsForCurrentUser() {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all MessagesPreferencesSettings for current user {}", currentUser);
        Set<MessagesPreferencesSetting> messagesPreferencesSettings = messagesPreferencesSettingRepository
                .findAllMessagesPreferencesSettingsForRecipientWithId(currentUser.getId());
        messagesPreferencesSettings = messagesPreferencesSettingsService.checkLoadedMessagePreferenceSettingsForCorrectness(messagesPreferencesSettings, currentUser);
        return new ResponseEntity<>(messagesPreferencesSettings, HttpStatus.OK);
    }

    /**
     * PUT messages-preferences-settings : Save messagesPreferencesSettings for current user
     *
     * Saves the provided messagesPreferencesSettings to the server.
     *
     * @param messagesPreferencesSettings which should be saved to the messagesPreferencesSettings database.
     * @return the MessagesPreferencesSettings that just got saved for the current user as array
     *         200 for a successful execution, 400 if the user provided empty settings to save, 500 if the save call returns empty settings
     */
    @PutMapping("messages-preferences-settings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MessagesPreferencesSetting[]> saveMessagesPreferencesSettingsForCurrentUser(
            @NotNull @RequestBody MessagesPreferencesSetting[] messagesPreferencesSettings) {
        if (messagesPreferencesSettings.length == 0) {
            throw new BadRequestAlertException("Cannot save non-existing MessagesPreferencesSettings", "MessagesPreferencesSettings", "messagesPreferencesSettingsEmpty");
        }
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to save MessagesPreferencesSettings : {} for current user {}", messagesPreferencesSettings, currentUser);
        messagesPreferencesSettingsService.setCurrentUser(messagesPreferencesSettings, currentUser);
        List<MessagesPreferencesSetting> resultAsList = messagesPreferencesSettingRepository.saveAll(Arrays.stream(messagesPreferencesSettings).toList());
        if (resultAsList.isEmpty()) {
            throw new BadRequestAlertException("Error occurred during saving of MessagePreferences Settings", "MessagesPreferencesSettings",
                    "messagesPreferencesSettingsEmptyAfterSave");
        }
        MessagesPreferencesSetting[] resultAsArray = resultAsList.toArray(new MessagesPreferencesSetting[0]);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "messagesPreferencesSetting", "test")).body(resultAsArray);
    }
}
