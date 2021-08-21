import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { OptionCore, OptionGroup, UserSettings, UserSettingsCategory, UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { Notification } from 'app/entities/notification.model';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: './notification-settings.component.html',
    styleUrls: ['../user-settings.component.scss'],
})
export class NotificationSettingsComponent implements OnInit {
    userSettingsCategory: UserSettingsCategory = UserSettingsCategory.NOTIFICATIONS;

    notificationSettings: UserSettings;
    optionsChanged: boolean = false;
    page = 0;
    error?: string;
    notificationOptionCores: Array<OptionCore> = new Array<OptionCore>();

    constructor(private notificationService: NotificationService, private userSettingsService: UserSettingsService, private changeDetector: ChangeDetectorRef) {}

    ngOnInit(): void {
        //this.loadNotificationOptions();
        //this.userSettingsService.loadUserOptions(this.userSettingsCategory, this.notificationOptionCores, this.notificationSettings);
        /*
        this.userSettingsService.loadUserSettings(this.userSettingsCategory)
            .subscribe((loadedSettings : UserSettings) => {
                this.notificationSettings = loadedSettings;
                this.notificationOptionCores = this.userSettingsService.extractOptionCoresFromSettings(loadedSettings);
            }
        );
         */
        this.userSettingsService.queryUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            this.notificationSettings = this.userSettingsService.loadUserOptionCoresSuccess(res.body!, res.headers, this.userSettingsCategory);
            this.notificationOptionCores = this.userSettingsService.extractOptionCoresFromSettings(this.notificationSettings);
            this.changeDetector.detectChanges();
            //(res: HttpErrorResponse) => (this.error = res.message) TODO
        });
    }

    saveOptions() {
        //TODO refresh notifications in notification-sidebar (else outdated, ngOnitnit only called once, i.e. only calls loadnotifications once)

        let newOptionCores = this.notificationOptionCores.filter((optionCore) => optionCore.changed);
        this.userSettingsService.saveUserOptions(newOptionCores).subscribe(
            (res: HttpResponse<OptionCore[]>) => this.saveUserOptionsSuccess(res.body!, res.headers),
            (res: HttpErrorResponse) => (this.error = res.message),
        );
    }

    private saveUserOptionsSuccess(receivedOptionCores: OptionCore[], headers: HttpHeaders): void {
        this.updateSettings(receivedOptionCores);
    }

    toggleOption(event: any) {
        this.optionsChanged = true;
        const optionId = event.currentTarget.id; //TODO
        let foundOption = this.notificationOptionCores.find((core) => core.optionSpecifier === optionId);
        if (!foundOption) return;
        foundOption!.webapp = !foundOption!.webapp;
        foundOption.changed = true;
    }

    private extractOptionCoresFromSettingsCategory(settings: UserSettings): OptionCore[] {
        let optionCoreAccumulator: OptionCore[] = [];
        settings.groups.forEach((group: OptionGroup) => {
            group.options.forEach((option) => {
                let optionCore: OptionCore = option.optionCore;
                if (optionCore.id == undefined) {
                    optionCore.id = -1; // is used to mark cores which have never been saved to the database
                }
                optionCoreAccumulator.push(optionCore);
            });
        });
        return optionCoreAccumulator;
    }

    private updateSettings(newOptionCores: OptionCore[]): void {
        //use the updated cores to update the entire settings category, needed for ids
        for (let i = 0; i < this.notificationSettings.groups.length; i++) {
            for (let j = 0; j < this.notificationSettings.groups[i].options.length; j++) {
                const currentOptionCore = this.notificationSettings.groups[i].options[j].optionCore;
                const matchingOptionCore = newOptionCores.find((newCore) => newCore.optionSpecifier === currentOptionCore.optionSpecifier);
                if (matchingOptionCore != undefined) {
                    this.notificationSettings.groups[i].options[j].optionCore = matchingOptionCore;
                }
            }
        }
        this.notificationOptionCores = this.extractOptionCoresFromSettingsCategory(this.notificationSettings);
    }

    private loadNotificationOptionCoresSuccess(receivedNotificationOptionCores: OptionCore[], headers: HttpHeaders): void {
        this.notificationSettings = defaultNotificationSettings;
        // if no option cores were loaded -> user has not yet changed options -> use default notification settings
        if (receivedNotificationOptionCores == undefined || receivedNotificationOptionCores.length === 0) {
            this.notificationOptionCores = this.extractOptionCoresFromSettingsCategory(defaultNotificationSettings);
            return;
        }
        // else user already customized the settings
        this.updateSettings(receivedNotificationOptionCores);
    }
}
