import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { OptionCore, UserSettings, UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    templateUrl: 'user-settings-prototype.component.html',
    styleUrls: ['user-settings-prototype.component.scss'],
})
/*abstract*/
export class UserSettingsPrototypeComponent implements OnInit {
    userSettingsCategory: string;
    changeEventMessage: string;
    userSettings: UserSettings;
    optionCores: Array<OptionCore> = new Array<OptionCore>();

    optionsChanged: boolean = false;
    //TODO remove
    showAlert: boolean = false;

    page = 0;
    error?: string;

    constructor(
        protected notificationService: NotificationService,
        protected userSettingsService: UserSettingsService,
        protected alertService: JhiAlertService,
        protected changeDetector: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.loadSetting();
    }

    loadSetting(): void {
        this.userSettingsService.loadUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            this.userSettings = this.userSettingsService.loadUserOptionCoresSuccessAsSettings(res.body!, res.headers, this.userSettingsCategory);
            this.finishUpdate();
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
                this.finishUpdate();
                this.finishSaving();
            },
            //(res: HttpErrorResponse) => (this.error = res.message), TODO
        );
    }

    protected finishUpdate(): void {
        this.optionCores = this.userSettingsService.extractOptionCoresFromSettings(this.userSettings);
        this.changeDetector.detectChanges();
    }

    protected finishSaving() {
        this.createApplyChangesEvent();
        this.optionsChanged = false;
        //this.alertService.addAlert({ type: 'success', msg: 'studentExam.submitSuccessful', timeout: 20000 }, []); //TODO
        //this.showAlert = true;
        this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert'); // TODO not working ...
    }

    protected createApplyChangesEvent(): void {
        this.userSettingsService.sendApplyChangesEvent(this.changeEventMessage);
    }
}
