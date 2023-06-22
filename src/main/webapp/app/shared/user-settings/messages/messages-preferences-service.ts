import { Injectable } from '@angular/core';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { MessagesPreferencesSetting } from 'app/shared/user-settings/messages/messages-preferences-structure';

export const MESSAGES_PREFERENCES_LINK_PREVIEW_TITLE = 'artemisApp.userSetting.messagesPreferences.title.linkPreview';

@Injectable({ providedIn: 'root' })
export class MessagesPreferencesService {
    /**
     * This is the place where the mapping between SettingIds and notification titles happens on the client side
     * Each SettingIds can be based on multiple different notification titles (based on NotificationTypes)
     */
    private static MESSAGES_PREFERENCES_SETTING_ID_TO_MESSAGES_PREFERENCES_TITLE_MAP: Map<SettingId, string[]> = new Map([
        [SettingId.MESSAGES_PREFERENCES__SHOW_LINK_PREVIEWS, [MESSAGES_PREFERENCES_LINK_PREVIEW_TITLE]],
    ]);

    // needed to make it possible for other services to get the latest settings without calling the server additional times
    private messagesPreferencesSettings: MessagesPreferencesSetting[] = [];

    public getMessagesPreferencesSettings(): MessagesPreferencesSetting[] {
        return this.messagesPreferencesSettings;
    }

    public setMessagesPreferencesSettings(notificationSettings: MessagesPreferencesSetting[]) {
        this.messagesPreferencesSettings = notificationSettings;
    }

    /**
     * Creates an updates map that indicates which messages (titles) are (de)activated in the current messages settings
     * @param messagesPreferencesSettings will be mapped to their respective title and create a new updated map
     * @return the updated map
     */
    public createUpdatedMessagesPreferencesTitleActivationMap(messagesPreferencesSettings: MessagesPreferencesSetting[]): Map<string, boolean> {
        const updatedMap: Map<string, boolean> = new Map<string, boolean>();
        let tmpMessagesPreferencesTitles: string[];

        for (let i = 0; i < messagesPreferencesSettings.length; i++) {
            tmpMessagesPreferencesTitles =
                MessagesPreferencesService.MESSAGES_PREFERENCES_SETTING_ID_TO_MESSAGES_PREFERENCES_TITLE_MAP.get(messagesPreferencesSettings[i].settingId) ?? [];
            if (tmpMessagesPreferencesTitles.length > 0) {
                tmpMessagesPreferencesTitles.forEach((tmpMessagesPreferencesTitle) => {
                    updatedMap.set(tmpMessagesPreferencesTitle, messagesPreferencesSettings[i].enabled!);
                });
            }
        }
        return updatedMap;
    }

    /**
     * Checks if the link preview is allowed by the current messages settings
     * @param messagesPreferencesTitleActivationMap  hold the information of the saved notification settings and their status
     */
    public isLinkPreviewPreferenceAllowedBySettings(messagesPreferencesTitleActivationMap: Map<string, boolean>): boolean {
        return messagesPreferencesTitleActivationMap.get(SettingId.MESSAGES_PREFERENCES__SHOW_LINK_PREVIEWS) ?? false;
    }
}
