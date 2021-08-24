import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Authority } from 'app/shared/constants/authority.constants';

/**
 * UserSettings represent one entire displayable settings page with detailed information like descriptions, etc.
 * Is used for displaying the settings page in html.
 * The OptionCores are uses as generics to support different implementations
 * Look at a x-settings.default.ts file for an example of the full UserSettings hierarchy
 */
export interface UserSettings<OptionCore> {
    category: UserSettingsCategory;
    groups: OptionGroup<OptionCore>[];
}

/**
 * OptionGroup is a simple group of options that have something in common,
 * e.g. they control notifications related to exercises
 */
export interface OptionGroup<OptionCore> {
    name: string;
    restrictionLevel: Authority;
    options: Option<OptionCore>[];
}

/**
 * Option represents one specific toggleable property the user can modify.
 * To make the database and server communication more lightweight and reduce redundant information
 * the constant properties of an option (name, description) are stored in x-settings.default.ts files
 * whereas the changeable properties (webapp, email : on/off) are encapsulated in an OptionCore
 */
export interface Option<OptionCore> {
    name: string;
    description: string;
    optionCore: OptionCore;
}

/**
 * OptionCores are used for client server communication and option (de)selection
 * Correspond to UserOptions (Server)
 */
export abstract class OptionCore {
    id?: number;
    optionSpecifier: string;
    changed?: boolean;
}
