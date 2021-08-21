import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { createRequestOption } from 'app/shared/util/request-util';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { User } from 'app/core/user/user.model';

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
    category: UserSettingsCategory;
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

    /*
    public loadUserOptions(category: UserSettingsCategory, optionCoresToLoad: OptionCore[], settingsToLoad: UserSettings): void {
            this.queryUserOptions(category).subscribe(
                (res: HttpResponse<OptionCore[]>) => this.loadUserOptionCoresSuccess(res.body!, res.headers,category, optionCoresToLoad, settingsToLoad),
                (res: HttpErrorResponse) => (this.error = res.message),
            );
    }
     */
    /*
    public loadUserSettings(category: UserSettingsCategory): Observable<UserSettings> {
        return this.queryUserOptions(category).subscribe((res: HttpResponse<OptionCore[]>) => {
             this.loadUserOptionCoresSuccess(res.body!, res.headers, category)
        });
        //(res: HttpErrorResponse) => this.error = res.message TODO
    }
    /*
    public loadUserOptions(category: UserSettingsCategory, optionCoresToLoad: OptionCore[], settingsToLoad: UserSettings): void {
        this.queryUserOptions(category).subscribe((res: HttpResponse<OptionCore[]>) => {
            return this.loadUserOptionCoresSuccess(res.body!, res.headers, category, optionCoresToLoad, settingsToLoad)
        });
        //(res: HttpErrorResponse) => this.error = res.message TODO
    }
     */

    public queryUserOptions(category: UserSettingsCategory): Observable<HttpResponse<OptionCore[]>> {
        debugger;
        const optionCores = createRequestOption(); //maybe useless TODO
        switch (category) {
            case UserSettingsCategory.NOTIFICATIONS: {
                return this.http.get<OptionCore[]>(this.resourceUrl + '/fetch-options', {
                    params: optionCores, //maybe useless TODO
                    observe: 'response',
                });
            }
        }
    }
    /*
    private loadUserOptionCoresSuccess(receivedOptionCoresFromServer: OptionCore[], headers: HttpHeaders, category: UserSettingsCategory, optionCoresToLoad: OptionCore[], settingsToLoad: UserSettings): void {
        debugger;
        let loadedOptionCores : OptionCore [];
        let loadedSettings : UserSettings;

        // load default settings as foundation
        switch(category) {
            case UserSettingsCategory.NOTIFICATIONS: {
                loadedSettings = defaultNotificationSettings;
            }
        }

        // if user already customized settings -> update loaded default settings with received data
        if (!(receivedOptionCoresFromServer == undefined || receivedOptionCoresFromServer.length === 0)) {
            this.updateSettings(receivedOptionCoresFromServer, loadedSettings);
        }
        //else continue using default settings

        loadedOptionCores = this.extractOptionCoresFromSettings(loadedSettings);

        optionCoresToLoad = loadedOptionCores;
        settingsToLoad = loadedSettings;
    }
 */
    public loadUserOptionCoresSuccess(receivedOptionCoresFromServer: OptionCore[], headers: HttpHeaders, category: UserSettingsCategory): UserSettings {
        debugger;
        let settingsResult: UserSettings;

        // load default settings as foundation
        switch (category) {
            case UserSettingsCategory.NOTIFICATIONS: {
                settingsResult = defaultNotificationSettings;
            }
        }

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

    private updateSettings(newOptionCores: OptionCore[], settingsToUpdate: UserSettings): void {
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

    saveUserOptions(optionCores: OptionCore[]): Observable<HttpResponse<OptionCore[]>> {
        return this.http.post<OptionCore[]>(this.resourceUrl + '/save-new-options', optionCores, { observe: 'response' });
    }
}
