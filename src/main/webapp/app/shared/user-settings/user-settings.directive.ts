import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Directive, OnInit, inject } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting, UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { AlertService } from 'app/core/util/alert.service';

/**
 * Is used as the abstract user-settings "parent" with all the necessary basic logic for other "child" components to implement/inherit from.
 */
@Directive()
export abstract class UserSettingsDirective implements OnInit {
    protected userSettingsService = inject(UserSettingsService);
    protected alertService = inject(AlertService);
    protected changeDetector = inject(ChangeDetectorRef);

    // HTML template related
    settingsChanged = false;
    currentUser: User;

    // userSettings logic related
    userSettingsCategory: UserSettingsCategory;
    changeEventMessage: string;
    userSettings: UserSettingsStructure<Setting>;
    settings: Array<Setting>;
    page = 0;
    error?: string;

    ngOnInit(): void {
        this.alertService.closeAll();
        this.loadSetting();
    }

    // methods related to loading

    /**
     * Fetches the settings based on the settings category, updates the currently loaded userSettingsStructure, individual settings, and HTML template
     */
    protected loadSetting(): void {
        this.userSettingsService.loadSettings(this.userSettingsCategory).subscribe({
            next: (res: HttpResponse<Setting[]>) => {
                this.userSettings = this.userSettingsService.loadSettingsSuccessAsSettingsStructure(res.body!, this.userSettingsCategory);
                this.settings = this.userSettingsService.extractIndividualSettingsFromSettingsStructure(this.userSettings);
                this.changeDetector.detectChanges();
                this.alertService.closeAll();
            },
            error: (res: HttpErrorResponse) => this.onError(res),
        });
    }

    // methods related to saving

    /**
     * Is invoked by clicking the save button
     * Sends all settings which were changed by the user to the server for saving
     */
    public saveSettings() {
        this.userSettingsService.saveSettings(this.settings, this.userSettingsCategory).subscribe({
            next: (res: HttpResponse<Setting[]>) => {
                this.userSettings = this.userSettingsService.saveSettingsSuccess(this.userSettings, res.body!);
                this.settings = this.userSettingsService.extractIndividualSettingsFromSettingsStructure(this.userSettings);
                this.finishSaving();
            },
            error: (res: HttpErrorResponse) => this.onError(res),
        });
    }

    /**
     * Finalizes the saving process by setting the HTML template back to an unchanged state (e.g. hides save-button),
     * informing the user by sending out an alert that the new settings were successfully saved,
     * and propagates this change to other components and services which are affected by these changed settings
     */
    protected finishSaving() {
        this.createApplyChangesEvent();
        this.settingsChanged = false;
        this.alertService.closeAll();
        this.alertService.success('artemisApp.userSettings.saveSettingsSuccessAlert');
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
     * Send out an error alert if an error occurred
     * @param httpErrorResponse which contains the error information
     */
    protected onError(httpErrorResponse: HttpErrorResponse) {
        const error = httpErrorResponse.error;
        if (error) {
            this.alertService.error(error.message, error.params);
        } else {
            this.alertService.error('error.unexpectedError', {
                error: httpErrorResponse.message,
            });
        }
    }
}
