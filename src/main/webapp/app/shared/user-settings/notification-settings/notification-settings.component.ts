import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { OptionCore, UserSettings, UserSettingsService } from 'app/shared/user-settings/user-settings.service';

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: './notification-settings.component.html',
    styleUrls: ['../user-settings.component.scss'],
})
export class NotificationSettingsComponent implements OnInit {
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
        this.userSettingsService.loadUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            this.notificationSettings = this.userSettingsService.loadUserOptionCoresSuccess(res.body!, res.headers, this.userSettingsCategory);
            this.notificationOptionCores = this.userSettingsService.extractOptionCoresFromSettings(this.notificationSettings);
            this.changeDetector.detectChanges();
            //(res: HttpErrorResponse) => (this.error = res.message) TODO
        });
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

    saveOptions() {
        //TODO refresh notifications in notification-sidebar (else outdated, ngOnitnit only called once, i.e. only calls loadnotifications once)
        this.userSettingsService.saveUserOptions(this.notificationOptionCores, this.userSettingsCategory).subscribe(
            (res: HttpResponse<OptionCore[]>) => {
                this.notificationSettings = this.userSettingsService.saveUserOptionsSuccess(res.body!, res.headers, this.userSettingsCategory);
                this.notificationOptionCores = this.userSettingsService.extractOptionCoresFromSettings(this.notificationSettings);
                this.changeDetector.detectChanges();
            },
            //(res: HttpErrorResponse) => (this.error = res.message), TODO
        );
    }
}
