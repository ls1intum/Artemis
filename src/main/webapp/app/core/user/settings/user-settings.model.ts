import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Authority } from 'app/shared/constants/authority.constants';

/**
 * UserSettingsStructures represent one entire displayable settings page with detailed information like descriptions, etc.
 * It is used for displaying the settings page in html.
 * The Settings are uses as generics to support multiple implementations for different settings pages
 * Look at x-settings-structure.ts file for an example of the full UserSettings hierarchy
 */
export interface UserSettingsStructure<T> {
    category: UserSettingsCategory;
    groups: SettingGroup<T>[];
}

/**
 * SettingGroup is a simple group of settings that have something in common,
 * e.g. they control notifications related to exercises
 * key will be replaced with the actual name during the translation
 */
export interface SettingGroup<T> {
    key: string;
    restrictionLevels: Authority[];
    settings: T[];
}

/**
 * One Setting represents a specific property the user can modify.
 * To avoid redundant entries in the database the constant properties of a setting
 * (name, description) are stored in x-settings-structure.ts files,
 * i.e. their respective keys, the full string is located in the translation jsons
 * whereas the changeable properties (e.g. webapp, email : on/off) are saved in the DB
 */
export abstract class Setting {
    // can not replace T with Setting due to shadowing rules of TSLint
    // generic is needed to make Setting polymorphic (e.g. for settings structures) otherwise compiler errors occur
    key?: string;
    descriptionKey?: string;
    settingId: SettingId;
    changed?: boolean;
}
