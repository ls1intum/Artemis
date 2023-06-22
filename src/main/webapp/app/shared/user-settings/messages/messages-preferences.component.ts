import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { UserSettingsDirective } from 'app/shared/user-settings/user-settings.directive';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { AlertService } from 'app/core/util/alert.service';
import { MessagesPreferencesService } from 'app/shared/user-settings/messages/messages-preferences-service';
import { faInfoCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MessagesPreferencesSetting } from 'app/shared/user-settings/messages/messages-preferences-structure';
import { SessionStorageService } from 'ngx-webstorage';

export const IS_LINK_PREVIEW_ENABLED_STORAGE_KEY = 'isLinkPreviewEnabled';

@Component({
    selector: 'jhi-messages-preferences',
    templateUrl: './messages-preferences.component.html',
    styleUrls: ['../user-settings.scss'],
})
export class MessagesPreferencesComponent extends UserSettingsDirective implements OnInit {
    // Icons
    faSave = faSave;
    faInfoCircle = faInfoCircle;
    constructor(
        userSettingsService: UserSettingsService,
        changeDetector: ChangeDetectorRef,
        alertService: AlertService,
        sessionStorageService: SessionStorageService,
        private messagesPreferencesService: MessagesPreferencesService,
    ) {
        super(userSettingsService, alertService, changeDetector, sessionStorageService);
    }

    declare userSettings: UserSettingsStructure<MessagesPreferencesSetting>;
    declare settings: Array<MessagesPreferencesSetting>;

    ngOnInit() {
        this.userSettingsCategory = UserSettingsCategory.MESSAGES_PREFERENCES_SETTINGS;

        // check if settings are already loaded
        const newestMessagesPreferencesSettings: MessagesPreferencesSetting[] = this.messagesPreferencesService.getMessagesPreferencesSettings();
        if (newestMessagesPreferencesSettings.length === 0) {
            // if no settings are already available load them from the server
            super.ngOnInit();
        } else {
            // else reuse the already available/loaded ones
            this.userSettings = this.userSettingsService.loadSettingsSuccessAsSettingsStructure(newestMessagesPreferencesSettings, this.userSettingsCategory);
            this.settings = this.userSettingsService.extractIndividualSettingsFromSettingsStructure(this.userSettings);
        }
    }

    /**
     * Catches the toggle event from a user click
     * Toggles the respective setting and mark it as changed (only changed settings will be send to the server for saving)
     */
    toggleSetting(event: any) {
        this.settingsChanged = true;
        let settingId = event.currentTarget.id;

        if (settingId.indexOf(' ') !== -1) {
            settingId = settingId.slice(0, settingId.indexOf(' '));
        }
        const settingToUpdate = this.settings.find((setting) => setting.settingId === settingId);
        if (!settingToUpdate) {
            return;
        }
        // toggle/inverts previous setting
        settingToUpdate!.enabled = !settingToUpdate!.enabled;
        settingToUpdate.changed = true;
    }
}
