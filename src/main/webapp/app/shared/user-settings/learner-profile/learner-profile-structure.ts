import { Authority } from 'app/shared/constants/authority.constants';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting, UserSettingsStructure } from '../user-settings.model';

export interface LearnerProfileSetting extends Setting {
    value: number;
    min: number;
    max: number;
    step: number;
}

export const learnerProfileStructure: UserSettingsStructure<LearnerProfileSetting> = {
    category: UserSettingsCategory.LEARNER_PROFILE,
    groups: [
        {
            key: 'learningStyle',
            restrictionLevels: [Authority.USER],
            settings: [
                {
                    key: 'practicalVsTheoretical',
                    descriptionKey: 'practicalVsTheoreticalDescription',
                    settingId: SettingId.LEARNER_PROFILE__LEARNING_STYLE__PRACTICAL_VS_THEORETICAL,
                    value: 0,
                    min: 0,
                    max: 2,
                    step: 1,
                    changed: false,
                },
                {
                    key: 'creativeVsFocused',
                    descriptionKey: 'creativeVsFocusedDescription',
                    settingId: SettingId.LEARNER_PROFILE__LEARNING_STYLE__CREATIVE_VS_FOCUSED,
                    value: 0,
                    min: 0,
                    max: 2,
                    step: 1,
                    changed: false,
                },
                {
                    key: 'followUpVsSummary',
                    descriptionKey: 'followUpVsSummaryDescription',
                    settingId: SettingId.LEARNER_PROFILE__LEARNING_STYLE__FOLLOW_UP_VS_SUMMARY,
                    value: 0,
                    min: 0,
                    max: 2,
                    step: 1,
                    changed: false,
                },
                {
                    key: 'briefVsDetailed',
                    descriptionKey: 'briefVsDetailedDescription',
                    settingId: SettingId.LEARNER_PROFILE__LEARNING_STYLE__BRIEF_VS_DETAILED,
                    value: 2,
                    min: 0,
                    max: 2,
                    step: 1,
                    changed: false,
                },
            ],
        },
    ],
};
