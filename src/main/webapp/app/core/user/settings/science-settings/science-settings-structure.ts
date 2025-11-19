import { Authority } from 'app/shared/constants/authority.constants';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting, UserSettingsStructure } from '../user-settings.model';

export interface ScienceSetting extends Setting {
    active?: boolean;
}

export const scienceSettingsStructure: UserSettingsStructure<ScienceSetting> = {
    category: UserSettingsCategory.SCIENCE_SETTINGS,
    groups: [
        {
            key: 'general',
            restrictionLevels: [Authority.STUDENT],
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
