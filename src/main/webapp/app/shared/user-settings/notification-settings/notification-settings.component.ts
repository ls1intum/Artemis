import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { OptionCore, OptionGroup, UserSettings, UserSettingsCategory, UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { Notification } from 'app/entities/notification.model';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: './notification-settings.component.html',
    styleUrls: ['../user-settings.component.scss'],
})
export class NotificationSettingsComponent implements OnInit {
    //userSettingsCategory: UserSettingsCategory = UserSettingsCategory.NOTIFICATIONS;
    userSettingsCategory = 'Notifications';

    notificationSettings: UserSettings;
    optionsChanged: boolean = false;
    page = 0;
    error?: string;
    notificationOptionCores: Array<OptionCore> = new Array<OptionCore>();

    constructor(private notificationService: NotificationService, private userSettingsService: UserSettingsService, private changeDetector: ChangeDetectorRef) {}

    ngOnInit(): void {
        this.loadSetting();
    }

    private loadSetting(): void {
        this.userSettingsService.queryUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            //this.notificationSettings = this.userSettingsService.loadUserOptionCoresSuccess(res.body!, res.headers, this.userSettingsCategory);
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
        //    this.updateSettings(receivedOptionCores);
        this.userSettingsService.updateSettings(receivedOptionCores, this.notificationSettings);
    }

    toggleOption(event: any) {
        debugger;
        this.optionsChanged = true;
        const optionId = event.currentTarget.id; //TODO
        let foundOption = this.notificationOptionCores.find((core) => core.optionSpecifier === optionId);
        if (!foundOption) return;
        foundOption!.webapp = !foundOption!.webapp;
        foundOption.changed = true;
    }
}
