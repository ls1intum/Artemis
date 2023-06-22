import { Setting, UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Authority } from 'app/shared/constants/authority.constants';

export interface MessagesPreferencesSetting extends Setting {
    // Status indicating if the settings was enabled by the user or the default settings
    enabled?: boolean;
}
export const messagesPreferencesStructure: UserSettingsStructure<MessagesPreferencesSetting> = {
    category: UserSettingsCategory.MESSAGES_PREFERENCES_SETTINGS,
    groups: [
        {
            key: 'linkPreview',
            restrictionLevels: [Authority.USER],
            settings: [
                {
                    key: 'linkPreviewPreference',
                    descriptionKey: 'linkPreviewPreferenceDescription',
                    settingId: SettingId.MESSAGES_PREFERENCES__SHOW_LINK_PREVIEWS,
                },
            ],
        },
    ],
};
