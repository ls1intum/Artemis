package de.tum.in.www1.artemis.service;

import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.MessagesPreferencesSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.MessagesPreferencesSettingRepository;

@Service
public class MessagesPreferencesSettingsService {

    private final MessagesPreferencesSettingRepository messagesPreferencesSettingRepository;

    // settings settingIds analogous to client side
    public static final String MESSAGES_PREFERENCES__SHOW_LINK_PREVIEWS = "messages-preferences.show-link-previews";

    public static final Set<MessagesPreferencesSetting> DEFAULT_MESSAGES_PREFERENCES_SETTINGS = new HashSet<>(
            List.of(new MessagesPreferencesSetting(false, MESSAGES_PREFERENCES__SHOW_LINK_PREVIEWS)));

    public MessagesPreferencesSettingsService(MessagesPreferencesSettingRepository messagesPreferencesSettingRepository) {
        this.messagesPreferencesSettingRepository = messagesPreferencesSettingRepository;
    }

    /**
     * Checks the personal messagesSettings retrieved from the DB.
     * If the loaded set is empty substitute it with the default settings
     * If the loaded set has different messages setting ids than the default settings both sets have to be merged
     *
     * @param userMessagesSettings are the messages preferences settings retrieved from the DB for the current user
     * @param user                 the user for which the settings should be loaded
     * @return the updated and correct messages settings
     */
    public Set<MessagesPreferencesSetting> checkLoadedMessagePreferenceSettingsForCorrectness(Set<MessagesPreferencesSetting> userMessagesSettings, User user) {
        if (userMessagesSettings.isEmpty()) {
            return DEFAULT_MESSAGES_PREFERENCES_SETTINGS;
        }
        // default settings might have changed (e.g. number of settings) -> need to merge the saved settings with default ones (else errors appear)

        if (!compareTwoMessagesPreferencesSettingsSetsBasedOnSettingsId(userMessagesSettings, DEFAULT_MESSAGES_PREFERENCES_SETTINGS)) {
            Set<MessagesPreferencesSetting> updatedDefaultMessagesPreferencesSettings = new HashSet<>(DEFAULT_MESSAGES_PREFERENCES_SETTINGS);

            userMessagesSettings.forEach(userMessagesPreferencesSetting -> DEFAULT_MESSAGES_PREFERENCES_SETTINGS.forEach(defaultSetting -> {
                if (defaultSetting.getSettingId().equals(userMessagesPreferencesSetting.getSettingId())) {
                    updatedDefaultMessagesPreferencesSettings.remove(defaultSetting);
                    updatedDefaultMessagesPreferencesSettings.add(userMessagesPreferencesSetting);
                }
            }));

            updatedDefaultMessagesPreferencesSettings.forEach(userMessagesSetting -> userMessagesSetting.setUser(user));
            // update DB to fix inconsistencies and avoid redundant future merges
            // first remove all settings of the current user in the DB
            messagesPreferencesSettingRepository.deleteAll(userMessagesSettings);
            // save correct merge to DB
            messagesPreferencesSettingRepository.saveAll(updatedDefaultMessagesPreferencesSettings);
            return updatedDefaultMessagesPreferencesSettings;
        }
        return userMessagesSettings;
    }

    /**
     * Compares two messagesPreferences settings sets based on their messagesPreferences setting ids
     *
     * @param settingsA is the first set
     * @param settingsB is the second set
     * @return true if the messagesPreferences setting ids of both are the same else return false
     */
    private boolean compareTwoMessagesPreferencesSettingsSetsBasedOnSettingsId(Set<MessagesPreferencesSetting> settingsA, Set<MessagesPreferencesSetting> settingsB) {
        Set<String> settingIdsA = extractSettingsIdsFromMessagesPreferencesSettingsSet(settingsA);
        Set<String> settingIdsB = extractSettingsIdsFromMessagesPreferencesSettingsSet(settingsB);
        return settingIdsA.equals(settingIdsB);
    }

    /**
     * Extracts the settingsIds of a messagesPreferences settings set
     * E.g. used to compare two sets of messagesPreferences settings based on setting id
     *
     * @param messagesPreferencesSettings set which setting ids should be extracted
     * @return a set of settings ids
     */
    private Set<String> extractSettingsIdsFromMessagesPreferencesSettingsSet(Set<MessagesPreferencesSetting> messagesPreferencesSettings) {
        Set<String> settingsIds = new HashSet<>();
        messagesPreferencesSettings.forEach(setting -> settingsIds.add(setting.getSettingId()));
        return settingsIds;
    }

    /**
     * Updates the messagesPreferencesSettings by setting the current user
     *
     * @param messagesPreferencesSettings which might be saved the very first time and have no user set yet
     * @param currentUser                 who should be set
     */
    public void setCurrentUser(MessagesPreferencesSetting[] messagesPreferencesSettings, User currentUser) {
        for (MessagesPreferencesSetting messagesPreferencesSetting : messagesPreferencesSettings) {
            messagesPreferencesSetting.setUser(currentUser);
        }
    }
}
