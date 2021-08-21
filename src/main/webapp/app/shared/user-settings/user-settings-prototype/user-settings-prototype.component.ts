import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { OptionCore, UserSettings, UserSettingsService } from 'app/shared/user-settings/user-settings.service';

@Component({
    templateUrl: 'user-settings-prototype.component.ts.html',
    styleUrls: ['../user-settings.component.scss'],
})
/*abstract*/
export class UserSettingsPrototypeComponent implements OnInit {
    userSettingsCategory: string;
    userSettings: UserSettings;
    optionCores: Array<OptionCore> = new Array<OptionCore>();

    optionsChanged: boolean = false;

    page = 0;
    error?: string;

    constructor(private notificationService: NotificationService, private userSettingsService: UserSettingsService, private changeDetector: ChangeDetectorRef) {}

    ngOnInit(): void {
        this.loadSetting();
    }

    loadSetting(): void {
        this.userSettingsService.loadUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            //this.notificationSettings = this.userSettingsService.loadUserOptionCoresSuccess(res.body!, res.headers, this.userSettingsCategory);
            this.userSettings = this.userSettingsService.loadUserOptionCoresSuccess(res.body!, res.headers, this.userSettingsCategory);
            this.finalizeUpdate();
            //(res: HttpErrorResponse) => (this.error = res.message) TODO
        });
    }

    toggleOption(event: any) {
        this.optionsChanged = true;
        const optionId = event.currentTarget.id; //TODO
        let foundOption = this.optionCores.find((core) => core.optionSpecifier === optionId);
        if (!foundOption) return;
        foundOption!.webapp = !foundOption!.webapp;
        foundOption.changed = true;
    }

    saveOptions() {
        //TODO refresh notifications in notification-sidebar (else outdated, ngOnitnit only called once, i.e. only calls loadnotifications once)
        this.userSettingsService.saveUserOptions(this.optionCores, this.userSettingsCategory).subscribe(
            (res: HttpResponse<OptionCore[]>) => {
                this.userSettings = this.userSettingsService.saveUserOptionsSuccess(res.body!, res.headers, this.userSettingsCategory);
                this.finalizeUpdate();
            },
            //(res: HttpErrorResponse) => (this.error = res.message), TODO
        );
    }

    private finalizeUpdate(): void {
        this.optionCores = this.userSettingsService.extractOptionCoresFromSettings(this.userSettings);
        this.changeDetector.detectChanges();
    }
}
