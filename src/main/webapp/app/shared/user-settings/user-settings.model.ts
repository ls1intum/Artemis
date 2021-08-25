import { OptionSpecifier, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Authority } from 'app/shared/constants/authority.constants';

/**
 * UserSettings represent one entire displayable settings page with detailed information like descriptions, etc.
 * Is used for displaying the settings page in html.
 * The OptionCores are uses as generics to support different implementations
 * Look at a x-settings.default.ts file for an example of the full UserSettings hierarchy
 */
export interface UserSettings<T> {
    category: UserSettingsCategory;
    groups: OptionGroup<T>[];
}

/**
 * OptionGroup is a simple group of options that have something in common,
 * e.g. they control notifications related to exercises
 */
export interface OptionGroup<T> {
    name: string;
    restrictionLevel: Authority;
    options: Option<T>[];
}

/**
 * Option represents one specific toggleable property the user can modify.
 * To make the database and server communication more lightweight and reduce redundant information
 * the constant properties of an option (name, description) are stored in x-settings.default.ts files
 * whereas the changeable properties (webapp, email : on/off) are encapsulated in an OptionCore
 */
export interface Option<T> {
    name: string;
    description: string;
    optionCore: T; // concrete OptionCore Implementation
}

/**
 * OptionCores are used for client server communication and option (de)selection
 * Correspond to UserOptions (Server)
 */
export abstract class OptionCore {
    id?: number;
    //optionSpecifier: string;
    optionSpecifier: OptionSpecifier;
    changed?: boolean;
}
