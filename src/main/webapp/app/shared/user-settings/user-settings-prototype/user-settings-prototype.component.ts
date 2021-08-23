import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { OptionCore, UserSettings, UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { JhiAlertService } from 'ng-jhipster';

/**
 * Is used as the "abstract" user-settings "parent" component with all the necessary basic logic for other "child" components to implement/inherit from.
 *
 * E.g. how to create a new "child"-settings.component : (Look at notification-settings as a prime example for child-settings)
 * 1) Create a new folder for childSettings and put childSettings specific files into it
 * 2.a) Use this component's html and scss in child-settings to reuse the same UI (as in notification-settings)
 * 2.b) Create a new child-settings.default.ts file and create your options based on the user-settings hierarchy
 *      (the hierarchy is explained in user-settings.service.ts (look at the interfaces))
 *      (look at notification-settings.default.ts for an example)
 * 2.c) Be careful and precise with the naming of new optionSpecifiers. Use them to create a mapping/correspondence to the actual changes in system-logic/behavior
 *      These names correspond with other places where mapping and translation take place (TranslationFile : userSettings.json)
 * 3.a) Create a new child-settings.component.ts file :
 *          Extend from this component and implement OnInit
 *          Place the relevant Services for this parent component in the constructor
 *          Inside ngOnInit() call super.ngOnInit(), afterwards set the child specific userSettingsCategory(same as in default.ts) and changeEventMessage
 * 4) For further child specific logic e.g. add a new child-settings.service file, new custom template/scss, etc.
 */
@Component({
    templateUrl: 'user-settings-prototype.component.html',
    styleUrls: ['user-settings-prototype.component.scss'],
})
export class UserSettingsPrototypeComponent implements OnInit {
    // HTML template related
    optionsChanged: boolean = false;
    showAlert: boolean = false;

    // userSettings logic related
    userSettingsCategory: string;
    changeEventMessage: string;
    userSettings: UserSettings;
    optionCores: Array<OptionCore> = new Array<OptionCore>();
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

    // HTML template related methods

    /**
     * Catches the toggle event from an user click
     * Toggles the respective option and mark at as changed (only changed option cores will be send to the server for saving)
     */
    toggleOption(event: any) {
        this.optionsChanged = true;
        const optionId = event.currentTarget.id;
        let foundOptionCore = this.optionCores.find((core) => core.optionSpecifier === optionId);
        if (!foundOptionCore) return;
        foundOptionCore!.webapp = !foundOptionCore!.webapp;
        foundOptionCore.changed = true;
    }

    // methods related to loading

    /**
     * Fetches the userOptionCores based on the settings category, updates the currently loaded userSettings, optionCores, and HTML template
     */
    loadSetting(): void {
        this.userSettingsService.loadUserOptions(this.userSettingsCategory).subscribe((res: HttpResponse<OptionCore[]>) => {
            this.userSettings = this.userSettingsService.loadUserOptionCoresSuccessAsSettings(res.body!, res.headers, this.userSettingsCategory);
            this.finishUpdate();
            //(res: HttpErrorResponse) => (this.error = res.message) TODO
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
            //(res: HttpErrorResponse) => (this.error = res.message), TODO
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
        //this.alertService.addAlert({ type: 'success', msg: 'studentExam.submitSuccessful', timeout: 20000 }, []); //TODO
        //this.showAlert = true;
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
