import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { createRequestOption } from 'app/shared/util/request-util';
//import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { User } from 'app/core/user/user.model';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import set = Reflect.set;
//import {defaultNotificationSettings} from "app/shared/user-settings/notification-settings/notification-settings.component";

/**
 * UserSettingsCategory are used for differentiation and support re-usability of service functionality
 */
export enum UserSettingsCategory {
    NOTIFICATIONS,
}

/**
 * UserSettings represent one entire displayable settings page with detailed information like descriptions, etc.
 * Is used for displaying the settings page in html.
 * Loot at any x-settings.default.ts file for an example of the full UserSettings hierarchy
 */
export interface UserSettings {
    //category: UserSettingsCategory;
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

    error?: string;

    constructor(private accountService: AccountService, private http: HttpClient) {}

    public queryUserOptions(category: string): Observable<HttpResponse<OptionCore[]>> {
        const optionCores = createRequestOption(); //maybe useless TODO

        //    switch (category) {
        //        case 'Notifications' : {
        return this.http.get<OptionCore[]>(this.resourceUrl + '/fetch-options', {
            params: optionCores, //maybe useless TODO
            observe: 'response',
        }); //  }}
    }

    private loadDefaultSettingsAsFoundation(category: string): UserSettings {
        switch (category) {
            case 'Notifications': {
                return defaultNotificationSettings;
            }
            default: {
                //TODO ERROR
                //with enums there was no such problem ...
                return defaultNotificationSettings;
            }
        }
    }

    public loadUserOptionCoresSuccess(receivedOptionCoresFromServer: OptionCore[], headers: HttpHeaders, category: string): UserSettings {
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

    public extractOptionCoresFromSettings(settings: UserSettings): OptionCore[] {
        let optionCoreAccumulator: OptionCore[] = [];
        settings.groups.forEach((group: OptionGroup) => {
            group.options.forEach((option: Option) => {
                let optionCore: OptionCore = option.optionCore;
                if (optionCore.id == undefined) {
                    optionCore.id = -1; // is used to mark cores which have never been saved to the database
                }
                optionCoreAccumulator.push(optionCore);
            });
        });
        return optionCoreAccumulator;
    }

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

    public saveUserOptions(optionCores: OptionCore[], category: string): Observable<HttpResponse<OptionCore[]>> {
        //only save cores which were changed
        let changedOptionCores = optionCores.filter((optionCore) => optionCore.changed);
        return this.http.post<OptionCore[]>(this.resourceUrl + '/save-new-options', changedOptionCores, { observe: 'response' });
    }

    public saveUserOptionsSuccess(receivedOptionCoresFromServer: OptionCore[], headers: HttpHeaders, category: string): UserSettings {
        let settingsResult: UserSettings;
        settingsResult = this.loadDefaultSettingsAsFoundation(category);
        this.updateSettings(receivedOptionCoresFromServer, settingsResult);
        return settingsResult;
    }
}
