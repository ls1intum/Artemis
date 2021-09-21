import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Observable, Subject } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { defaultNotificationSettings, NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting, SettingGroup, UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';

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
    public loadSettings(category: UserSettingsCategory): Observable<HttpResponse<Setting[]>> {
        switch (category) {
            case UserSettingsCategory.NOTIFICATION_SETTINGS: {
                return this.http.get<NotificationSetting[]>(this.notificationSettingsResourceUrl, { observe: 'response' });
            }
        }
    }

    /**
     * Is called after a successful server call to load user options
     * The fetched option cores are used to update the given settings
     * @param receivedSettingsFromServer were loaded from the server to update provided settings
     * @param category decided what default settings to use as the base
     * @return updated UserSettings based on loaded option cores
     */
    public loadSettingsSuccessAsSettingsStructure(receivedSettingsFromServer: Setting[], category: UserSettingsCategory): UserSettingsStructure<Setting> {
        let settingsResult: UserSettingsStructure<Setting>;
        // load default settings as foundation
        settingsResult = UserSettingsService.loadDefaultSettingsStructureAsFoundation(category);

        // if user already customized settings -> update loaded default settings with received data
        if (!(receivedSettingsFromServer == undefined || receivedSettingsFromServer.length === 0)) {
            this.updateSettingsStructure(receivedSettingsFromServer, settingsResult);
        }
        // else continue using default settings
        return settingsResult;
    }

    /**
     * Is called after a successful server call to load user options
     * The fetched option cores are used to create updated settings
     * Afterwards these settings are used to extract every (also non changed) option cores
     * @param receivedSettingsFromServer were loaded from the server to update provided settings
     * @param category decided what default settings to use as the base
     * @return all option cores based on the updated settings
     */
    public loadSettingsSuccessAsSettings(receivedSettingsFromServer: Setting[], category: UserSettingsCategory): Setting[] {
        const settingsResult = this.loadSettingsSuccessAsSettingsStructure(receivedSettingsFromServer, category);
        return this.extractSettingsFromSettingsStructure(settingsResult);
    }

    // save methods

    /**
     * Saves only the changed options (cores) to the database.
     * @param settings all options of the given UserSettings which will be filtered
     * @param category limits the server call to only search for options based on the provided category
     * @return the saved user options (cores) which were found in the database (for validation) or error
     */
    public saveSettings(settings: Setting[], category: UserSettingsCategory): Observable<HttpResponse<Setting[]>> {
        // only save cores which were changed
        const changedSettings = settings.filter((setting) => setting.changed);

        switch (category) {
            case UserSettingsCategory.NOTIFICATION_SETTINGS: {
                return this.http.put<Setting[]>(this.notificationSettingsResourceUrl, changedSettings, { observe: 'response' });
            }
        }
    }

    /**
     * Is called after a successful server call to save changed user options
     * The fetched option cores are used to update the given (current) settings (for validation)
     * @param settingsStructureToUpdate (usually the current settings prior to saving)
     * @param receivedSettingsFromServer were loaded from the server to update provided settings
     * @return updated UserSettings based on loaded option cores
     */
    public saveSettingsSuccess(settingsStructureToUpdate: UserSettingsStructure<Setting>, receivedSettingsFromServer: Setting[]): UserSettingsStructure<Setting> {
        this.updateSettingsStructure(receivedSettingsFromServer, settingsStructureToUpdate);
        return settingsStructureToUpdate;
    }

    // auxiliary methods

    /**
     * Extracts the individual option (cores) out of the UserSetting hierarchy.
     * @param settingsStructure which option cores should be extracted
     * @return OptionCore array based on the provided UserSettings
     */
    public extractSettingsFromSettingsStructure(settingsStructure: UserSettingsStructure<Setting>): Setting[] {
        const settingAccumulator: Setting[] = [];
        settingsStructure.groups.forEach((group: SettingGroup<Setting>) => {
            group.settings.forEach((setting: Setting) => {
                // sets changed flag to false after update
                setting.changed = false;
                settingAccumulator.push(setting);
            });
        });
        return settingAccumulator;
    }

    /**
     * Updates the provided settings based on the new option cores
     * @param newSettings received from the server
     * @param settingsStructureToUpdate will be updated by replacing matching options with new option cores
     */
    private updateSettingsStructure(newSettings: Setting[], settingsStructureToUpdate: UserSettingsStructure<Setting>): void {
        for (let i = 0; i < settingsStructureToUpdate.groups.length; i++) {
            for (let j = 0; j < settingsStructureToUpdate.groups[i].settings.length; j++) {
                const currentSetting = settingsStructureToUpdate.groups[i].settings[j];
                const matchingSetting = newSettings.find((newSetting) => newSetting.settingId === currentSetting.settingId);
                if (matchingSetting != undefined) {
                    //settingsStructureToUpdate.groups[i].settings[j] = matchingSetting;
                    Object.assign(settingsStructureToUpdate.groups[i].settings[j], matchingSetting);
                }
            }
        }
    }

    /**
     * Provides the foundation with all options for further modification.
     * @param category defines what default settings to return
     * @return the default settings based on the provided category
     */
    private static loadDefaultSettingsStructureAsFoundation(category: UserSettingsCategory): UserSettingsStructure<Setting> {
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
