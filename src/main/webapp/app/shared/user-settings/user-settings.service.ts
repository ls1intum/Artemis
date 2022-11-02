import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Observable, Subject } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { NotificationSetting, notificationSettingsStructure } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
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
     * GET call to the server to receive the stored settings of the current user
     * or default settings if the user has not yet changed the settings
     * @param category limits the server call to only search for settings based on the provided category
     * @return the saved user settings which were found in the database or default settings or error
     */
    public loadSettings(category: UserSettingsCategory): Observable<HttpResponse<Setting[]>> {
        switch (category) {
            case UserSettingsCategory.NOTIFICATION_SETTINGS: {
                return this.http.get<NotificationSetting[]>(this.notificationSettingsResourceUrl, { observe: 'response' });
            }
        }
    }

    /**
     * Is called after a successful server call to load user settings
     * The fetched settings are used to update the given settings structure
     * @param receivedSettingsFromServer were loaded from the server to update provided settings structure
     * @param category decided what settings structure to use
     * @return updated settings structure based on loaded settings
     */
    public loadSettingsSuccessAsSettingsStructure(receivedSettingsFromServer: Setting[], category: UserSettingsCategory): UserSettingsStructure<Setting> {
        let settingsResult: UserSettingsStructure<Setting>;
        // load structure as foundation
        settingsResult = UserSettingsService.loadSettingsStructure(category);
        this.updateSettingsStructure(receivedSettingsFromServer, settingsResult);
        return settingsResult;
    }

    /**
     * Is called after a successful server call to load user settings
     * The fetched settings are used to create the settings structure needed for the template
     * Afterwards the settings structure is used to extract updated individual settings
     * @param receivedSettingsFromServer were loaded from the server to update provided settings structure
     * @param category decided what settings structure to use
     * @return all individual settings based on the updated settings structure
     */
    public loadSettingsSuccessAsIndividualSettings(receivedSettingsFromServer: Setting[], category: UserSettingsCategory): Setting[] {
        const settingsResult = this.loadSettingsSuccessAsSettingsStructure(receivedSettingsFromServer, category);
        return this.extractIndividualSettingsFromSettingsStructure(settingsResult);
    }

    // save methods

    /**
     * Saves all settings to the database.
     * @param settings all settings of the given settings structure
     * @param category limits the server call to only search for settings based on the provided category
     * @return all saved user settings which were found in the database (for validation) or error
     */
    public saveSettings(settings: Setting[], category: UserSettingsCategory): Observable<HttpResponse<Setting[]>> {
        switch (category) {
            case UserSettingsCategory.NOTIFICATION_SETTINGS: {
                return this.http.put<Setting[]>(this.notificationSettingsResourceUrl, settings, { observe: 'response' });
            }
        }
    }

    /**
     * Is called after a successful server call to save settings
     * The fetched individual settings are used to update the given (current) settings structure (for validation)
     * @param settingsStructureToUpdate (usually the current settings structure prior to saving)
     * @param receivedSettingsFromServer were loaded from the server to update provided settings structure
     * @return updated UserSettings structure based on loaded individual settings
     */
    public saveSettingsSuccess(settingsStructureToUpdate: UserSettingsStructure<Setting>, receivedSettingsFromServer: Setting[]): UserSettingsStructure<Setting> {
        this.updateSettingsStructure(receivedSettingsFromServer, settingsStructureToUpdate);
        return settingsStructureToUpdate;
    }

    // auxiliary methods

    /**
     * Extracts the individual settings out of the UserSetting structure (hierarchy).
     * @param settingsStructure where the settings should be extracted from
     * @return setting array based on the provided settings structure
     */
    public extractIndividualSettingsFromSettingsStructure(settingsStructure: UserSettingsStructure<Setting>): Setting[] {
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
     * Updates the provided settings structure based on the new individual settings
     * @param newSettings received from the server
     * @param settingsStructureToUpdate will be updated by replacing or merging matching settings
     */
    private updateSettingsStructure(newSettings: Setting[], settingsStructureToUpdate: UserSettingsStructure<Setting>): void {
        for (let i = 0; i < settingsStructureToUpdate.groups.length; i++) {
            for (let j = 0; j < settingsStructureToUpdate.groups[i].settings.length; j++) {
                const currentSetting = settingsStructureToUpdate.groups[i].settings[j];
                const matchingSetting = newSettings.find((newSetting) => newSetting.settingId === currentSetting.settingId);
                if (matchingSetting != undefined) {
                    Object.assign(settingsStructureToUpdate.groups[i].settings[j], matchingSetting);
                }
            }
        }
    }

    /**
     * Provides the foundation for further modification and to be displayed in the template.
     * @param category defines what settings structure to return
     * @return the settings structure based on the provided category
     */
    private static loadSettingsStructure(category: UserSettingsCategory): UserSettingsStructure<Setting> {
        switch (category) {
            case UserSettingsCategory.NOTIFICATION_SETTINGS: {
                return notificationSettingsStructure;
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
