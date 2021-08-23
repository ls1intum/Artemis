import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Observable, Subject } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { createRequestOption } from 'app/shared/util/request-util';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';

/**
 * UserSettings represent one entire displayable settings page with detailed information like descriptions, etc.
 * Is used for displaying the settings page in html.
 * Loot at any x-settings.default.ts file for an example of the full UserSettings hierarchy
 */
export interface UserSettings {
    category: string;
    groups: OptionGroup[];
}

/**
 * OptionGroup is a simple group of options that have something in common,
 * e.g. they control notifications related to exercises
 */
export interface OptionGroup {
    name: string;
    // todo securityLevel : STUDENT (new enum), maybe add this to settings category and option as well
    options: Option[];
}

/**
 * Option represents one specific toggleable property the user can modify.
 * To make the database and server communication more lightweight and reduce redundant information
 * the constant properties of an option (name, description) are stored in x-settings.default.ts files
 * whereas the changeable properties (webapp, email : on/off) are encapsulated in an OptionCore
 */
export interface Option {
    name: string;
    description: string;
    optionCore: OptionCore;
}

/**
 * OptionCores are used for client server communication and option (de)selection
 * Correspond to UserOptions (Server)
 */
export interface OptionCore {
    id?: number;
    optionSpecifier: string;
    webapp: boolean;
    email?: boolean;
    changed?: boolean;
}

@Injectable({ providedIn: 'root' })
export class UserSettingsService {
    public resourceUrl = SERVER_API_URL + 'api/user-settings';
    private applyNewChangesSource = new Subject<String>();
    userSettingsChangeEvent = this.applyNewChangesSource.asObservable();
    error?: string;

    constructor(private accountService: AccountService, private http: HttpClient) {}

    // load methods

    /**
     * GET call to the server to receive the stored option cores of the current user
     * @param categpry limits the server call to only search for options based on the provided category
     * @return the saved user options (cores) which were found in the database (might be 0 if user has never saved settings before) or error
     */
    public loadUserOptions(category: string): Observable<HttpResponse<OptionCore[]>> {
        const optionCores = createRequestOption(); //maybe useless TODO
        //    switch (category) {
        //        case 'Notifications' : {
        return this.http.get<OptionCore[]>(this.resourceUrl + '/fetch-options', {
            params: optionCores, //maybe useless TODO
            observe: 'response',
        }); //  }}
    }

    /**
     * Is called after a successful server call to load user options
     * The fetched option cores are used to update the given settings
     * @param receivedOptionCoresFromServer were loaded from the server to update provided settings
     * @param category decided what default settings to use as the base
     * @return updated UserSettings based on loaded option cores
     */
    public loadUserOptionCoresSuccessAsSettings(receivedOptionCoresFromServer: OptionCore[], headers: HttpHeaders, category: string): UserSettings {
        let settingsResult: UserSettings;
        // load default settings as foundation
        settingsResult = this.loadDefaultSettingsAsFoundation(category);

        // if user already customized settings -> update loaded default settings with received data
        if (!(receivedOptionCoresFromServer == undefined || receivedOptionCoresFromServer.length === 0)) {
            this.updateSettings(receivedOptionCoresFromServer, settingsResult);
        }
        //else continue using default settings
        return settingsResult;
    }

    /**
     * Is called after a successful server call to load user options
     * The fetched option cores are used to create updated settings
     * Afterwards these settings are used to extract every (also non changed) option cores
     * @param receivedOptionCoresFromServer were loaded from the server to update provided settings
     * @param category decided what default settings to use as the base
     * @return all option cores based on the updated settings
     */
    public loadUserOptionCoresSuccessAsOptionCores(receivedOptionCoresFromServer: OptionCore[], headers: HttpHeaders, category: string): OptionCore[] {
        let settingsResult: UserSettings;
        // load default settings as foundation
        settingsResult = this.loadDefaultSettingsAsFoundation(category);

        // if user already customized settings -> update loaded default settings with received data
        if (!(receivedOptionCoresFromServer == undefined || receivedOptionCoresFromServer.length === 0)) {
            this.updateSettings(receivedOptionCoresFromServer, settingsResult);
        }
        //else continue using default settings and return only option cores (e.g. used for filtering)
        return this.extractOptionCoresFromSettings(settingsResult);
    }

    // save methods

    /**
     * Saves only the changed options (cores) to the database.
     * @param optionCores all options of the given UserSettings which will be filtered
     * @param categpry limits the server call to only search for options based on the provided category
     * @return the saved user options (cores) which were found in the database (for validation) or error
     */
    public saveUserOptions(optionCores: OptionCore[], category: string): Observable<HttpResponse<OptionCore[]>> {
        //only save cores which were changed
        let changedOptionCores = optionCores.filter((optionCore) => optionCore.changed);
        return this.http.post<OptionCore[]>(this.resourceUrl + '/save-options', changedOptionCores, { observe: 'response' });
    }

    /**
     * Is called after a successful server call to save changed user options
     * The fetched option cores are used to update the given settings (for validation)
     * @param receivedOptionCoresFromServer were loaded from the server to update provided settings
     * @param category decided what default settings to use as the base
     * @return updated UserSettings based on loaded option cores
     */
    public saveUserOptionsSuccess(receivedOptionCoresFromServer: OptionCore[], headers: HttpHeaders, category: string): UserSettings {
        let settingsResult: UserSettings;
        settingsResult = this.loadDefaultSettingsAsFoundation(category);
        this.updateSettings(receivedOptionCoresFromServer, settingsResult);
        return settingsResult;
    }

    // auxiliary methods

    /**
     * Extracts the individual option (cores) out of the UserSetting hierarchy.
     * @param settings which option cores should be extracted
     * @return OptionCore array based on the provided UserSettings
     */
    public extractOptionCoresFromSettings(settings: UserSettings): OptionCore[] {
        let optionCoreAccumulator: OptionCore[] = [];
        settings.groups.forEach((group: OptionGroup) => {
            group.options.forEach((option: Option) => {
                let optionCore: OptionCore = option.optionCore;
                if (optionCore.id == undefined) {
                    optionCore.id = -1; // is used to mark cores which have never been saved to the database
                }
                //sets changed flag to false after update
                optionCore.changed = false;
                optionCoreAccumulator.push(optionCore);
            });
        });
        return optionCoreAccumulator;
    }

    /**
     * Updates the provided settings based on the new option cores
     * @param newOptionCores received from the server
     * @param settingsToUpdate will be updated by replacing matching options with new option cores
     */
    public updateSettings(newOptionCores: OptionCore[], settingsToUpdate: UserSettings): void {
        //use the updated cores to update the entire settings category, needed for ids
        for (let i = 0; i < settingsToUpdate.groups.length; i++) {
            for (let j = 0; j < settingsToUpdate.groups[i].options.length; j++) {
                const currentOptionCore = settingsToUpdate.groups[i].options[j].optionCore;
                const matchingOptionCore = newOptionCores.find((newCore) => newCore.optionSpecifier === currentOptionCore.optionSpecifier);
                if (matchingOptionCore != undefined) {
                    settingsToUpdate.groups[i].options[j].optionCore = matchingOptionCore;
                }
            }
        }
    }

    /**
     * Provides the foundation with all options for further modification.
     * @param category defines what default settings to return
     * @return the default settings based on the provided category
     */
    private loadDefaultSettingsAsFoundation(category: string): UserSettings {
        switch (category) {
            case 'notificationSettings': {
                return defaultNotificationSettings;
            }
            default: {
                //TODO ERROR
                //with enums there was no such problem ...
                return defaultNotificationSettings;
            }
        }
    }

    /**
     * Sends messages to subscribed observers.
     * If a fitting message is received, specific observers will react to the changed settings they are affected by.
     * E.g. notification-settings component starts an event to reload the notifications displayed in the notification side bar
     */
    public sendApplyChangesEvent(message: String): void {
        this.applyNewChangesSource.next(message);
    }
}
