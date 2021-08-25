import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { JhiAlertService } from 'ng-jhipster';
import { User } from 'app/core/user/user.model';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { OptionCore, UserSettings } from 'app/shared/user-settings/user-settings.model';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';

/**
 * Is used as the "abstract" user-settings "parent" component with all the necessary basic logic for other "child" components to implement/inherit from.
 */
@Component({
    templateUrl: 'user-settings-prototype.component.html',
    styleUrls: ['user-settings-prototype.component.scss'],
})
export class UserSettingsPrototypeComponent implements OnInit {
    // HTML template related
    optionsChanged = false;
    currentUser: User;

    // userSettings logic related
    userSettingsCategory: UserSettingsCategory;
    changeEventMessage: string;
    userSettings: UserSettings<OptionCore>;
    optionCores: Array<OptionCore>;
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

    // methods related to loading

    /**
     * Fetches the userOptionCores based on the settings category, updates the currently loaded userSettings, optionCores, and HTML template
     */
    loadSetting(): void {
        this.userSettingsService.loadUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            this.userSettings = this.userSettingsService.loadUserOptionCoresSuccessAsSettings(res.body!, this.userSettingsCategory);
            this.finishUpdate();
            // (res: HttpErrorResponse) => (this.error = res.message) TODO
        });
    }

    // methods related to saving

    /**
     * Is invoked by clicking the save button
     * Sends all option cores which were changed by the user to the server for saving
     */
    saveOptions() {
        this.userSettingsService.saveUserOptions(this.optionCores, this.userSettingsCategory).subscribe(
            (res: HttpResponse<OptionCore[]>) => {
                this.userSettings = this.userSettingsService.saveUserOptionsSuccess(res.body!, res.headers, this.userSettingsCategory);
                this.finishUpdate();
                this.finishSaving();
            },
            // (res: HttpErrorResponse) => (this.error = res.message), TODO
        );
    }

    /**
     * Finalizes the saving process by setting the HTML template back to an unchanged state (e.g. hides save-button),
     * informing the user by sending out an alert that the new settings were successfully saved,
     * and propagates this change to other components and services which are affected by these changed settings
     */
    protected finishSaving() {
        this.createApplyChangesEvent();
        this.optionsChanged = false;
        // this.alertService.addAlert({ type: 'success', msg: 'studentExam.submitSuccessful', timeout: 20000 }, []); //TODO
        this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert'); // TODO not working ...
    }

    /**
     * Invokes the sendApplyChangesEvent method in userSettingsService and inserts the custom changeEventMessage
     * This message is set in the ngOnInit() of a "child"-settings.component
     */
    protected createApplyChangesEvent(): void {
        this.userSettingsService.sendApplyChangesEvent(this.changeEventMessage);
    }

    // auxiliary methods

    /**
     * Finalizes the loading or saving process by updating the optionCores (based on the newly updated settings) and the HTML template
     */
    protected finishUpdate(): void {
        this.optionCores = this.userSettingsService.extractOptionCoresFromSettings(this.userSettings);
        this.changeDetector.detectChanges();
    }
}
