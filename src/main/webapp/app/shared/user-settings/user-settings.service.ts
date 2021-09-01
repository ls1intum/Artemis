import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Observable, Subject } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { defaultNotificationSettings, NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Option, OptionCore, OptionGroup, UserSettings } from 'app/shared/user-settings/user-settings.model';

@Injectable({ providedIn: 'root' })
export class UserSettingsService {
    public notificationSettingsResourceUrl = SERVER_API_URL + 'api/notification-settings';
    private applyNewChangesSource = new Subject<string>();
    userSettingsChangeEvent = this.applyNewChangesSource.asObservable();
    error?: string;

    constructor(private accountService: AccountService, private http: HttpClient) {}

    // load methods

    /**
     * GET call to the server to receive the stored option cores of the current user
     * @param category limits the server call to only search for options based on the provided category
     * @return the saved user options (cores) which were found in the database (might be 0 if user has never saved settings before) or error
     */
    public loadUserOptions(category: UserSettingsCategory): Observable<HttpResponse<OptionCore[]>> {
        switch (category) {
            case UserSettingsCategory.NOTIFICATION_SETTINGS: {
                return this.http.get<NotificationOptionCore[]>(this.notificationSettingsResourceUrl + '/fetch-options', { observe: 'response' });
            }
        }
    }

    /**
     * Is called after a successful server call to load user options
     * The fetched option cores are used to update the given settings
     * @param receivedOptionCoresFromServer were loaded from the server to update provided settings
     * @param category decided what default settings to use as the base
     * @return updated UserSettings based on loaded option cores
     */
    public loadUserOptionCoresSuccessAsSettings(receivedOptionCoresFromServer: OptionCore[], category: UserSettingsCategory): UserSettings<OptionCore> {
        let settingsResult: UserSettings<OptionCore>;
        // load default settings as foundation
        settingsResult = UserSettingsService.loadDefaultSettingsAsFoundation(category);

        // if user already customized settings -> update loaded default settings with received data
        if (!(receivedOptionCoresFromServer == undefined || receivedOptionCoresFromServer.length === 0)) {
            this.updateSettings(receivedOptionCoresFromServer, settingsResult);
        }
        // else continue using default settings
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
    public loadUserOptionCoresSuccessAsOptionCores(receivedOptionCoresFromServer: OptionCore[], category: UserSettingsCategory): OptionCore[] {
        const settingsResult = this.loadUserOptionCoresSuccessAsSettings(receivedOptionCoresFromServer, category);
        return this.extractOptionCoresFromSettings(settingsResult);
    }

    // save methods

    /**
     * Saves only the changed options (cores) to the database.
     * @param optionCores all options of the given UserSettings which will be filtered
     * @param category limits the server call to only search for options based on the provided category
     * @return the saved user options (cores) which were found in the database (for validation) or error
     */
    public saveUserOptions(optionCores: OptionCore[], category: UserSettingsCategory): Observable<HttpResponse<OptionCore[]>> {
        // only save cores which were changed
        const changedOptionCores = optionCores.filter((optionCore) => optionCore.changed);
        switch (category) {
            case UserSettingsCategory.NOTIFICATION_SETTINGS: {
                return this.http.post<OptionCore[]>(this.notificationSettingsResourceUrl + '/save-options', changedOptionCores, { observe: 'response' });
            }
        }
    }

    /**
     * Is called after a successful server call to save changed user options
     * The fetched option cores are used to update the given (current) settings (for validation)
     * @param settingsToUpdate (usually the current settings prior to saving)
     * @param receivedOptionCoresFromServer were loaded from the server to update provided settings
     * @return updated UserSettings based on loaded option cores
     */
    public saveUserOptionsSuccess(settingsToUpdate: UserSettings<OptionCore>, receivedOptionCoresFromServer: OptionCore[]): UserSettings<OptionCore> {
        this.updateSettings(receivedOptionCoresFromServer, settingsToUpdate);
        return settingsToUpdate;
    }

    // auxiliary methods

    /**
     * Extracts the individual option (cores) out of the UserSetting hierarchy.
     * @param settings which option cores should be extracted
     * @return OptionCore array based on the provided UserSettings
     */
    public extractOptionCoresFromSettings(settings: UserSettings<OptionCore>): OptionCore[] {
        const optionCoreAccumulator: OptionCore[] = [];
        settings.groups.forEach((group: OptionGroup<OptionCore>) => {
            group.options.forEach((option: Option<OptionCore>) => {
                const optionCore: OptionCore = option.optionCore;
                // sets changed flag to false after update
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
    private updateSettings(newOptionCores: OptionCore[], settingsToUpdate: UserSettings<OptionCore>): void {
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
    private static loadDefaultSettingsAsFoundation(category: UserSettingsCategory): UserSettings<OptionCore> {
        switch (category) {
            case UserSettingsCategory.NOTIFICATION_SETTINGS: {
                return defaultNotificationSettings;
            }
        }
    }

    /**
     * Sends messages to subscribed observers.
     * If a fitting message is received, specific observers will react to the changed settings they are affected by.
     * E.g. notification-settings component starts an event to reload the notifications displayed in the notification side bar
     */
    public sendApplyChangesEvent(message: string): void {
        this.applyNewChangesSource.next(message);
    }
}
