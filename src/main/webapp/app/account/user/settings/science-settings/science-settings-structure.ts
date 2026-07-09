import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';
import { SettingId, UserSettingsCategory } from 'app/foundation/constants/user-settings.constants';
import { Setting, UserSettingsStructure } from '../user-settings.model';

// Science settings are plain user settings; the on/off state (`active`) lives on the base `Setting`.
export type ScienceSetting = Setting;

export const scienceSettingsStructure: UserSettingsStructure<ScienceSetting> = {
    category: UserSettingsCategory.SCIENCE_SETTINGS,
    groups: [
        {
            key: 'general',
            restrictionLevels: IS_AT_LEAST_STUDENT,
            settings: [
                {
                    key: 'activity',
                    descriptionKey: 'activityDescription',
                    settingId: SettingId.SCIENCE__GENERAL__ACTIVITY_TRACKING,
                    active: true,
                },
            ],
        },
    ],
};
