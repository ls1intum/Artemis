import { Component, DoCheck, Input, OnInit } from '@angular/core';
import { IrisSettings, IrisSettingsType } from 'app/entities/iris/settings/iris-settings.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { ButtonType } from 'app/shared/components/button.component';
import { faRotate, faSave } from '@fortawesome/free-solid-svg-icons';
import { IrisModel } from 'app/entities/iris/settings/iris-model';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { cloneDeep, isEqual } from 'lodash-es';
import {
    IrisChatSubSettings,
    IrisCompetencyGenerationSubSettings,
    IrisHestiaSubSettings,
    IrisLectureIngestionSubSettings,
    IrisProactivitySubSettings,
} from 'app/entities/iris/settings/iris-sub-settings.model';
import { AccountService } from 'app/core/auth/account.service';
import {
    IrisEventSettings,
    IrisEventType,
    JolEventSettings,
    SubmissionFailedEventSettings,
    SubmissionSuccessfulEventSettings,
} from 'app/entities/iris/settings/iris-event-settings.model';

@Component({
    selector: 'jhi-iris-settings-update',
    templateUrl: './iris-settings-update.component.html',
})
export class IrisSettingsUpdateComponent implements OnInit, DoCheck, ComponentCanDeactivate {
    @Input()
    public settingsType: IrisSettingsType;
    @Input()
    public courseId?: number;
    @Input()
    public exerciseId?: number;
    public irisSettings?: IrisSettings;
    public parentIrisSettings?: IrisSettings;
    public allIrisModels?: IrisModel[];
    public parentIrisEventSettings?: { [key: string]: IrisEventSettings };

    originalIrisSettings?: IrisSettings;

    public autoLectureIngestion = false;

    // Status bools
    isLoading = false;
    isSaving = false;
    isDirty = false;
    isAdmin: boolean;
    // Button types
    PRIMARY = ButtonType.PRIMARY;
    WARNING = ButtonType.WARNING;
    SUCCESS = ButtonType.SUCCESS;
    // Icons
    faSave = faSave;
    faRotate = faRotate;
    // Settings types
    GLOBAL = IrisSettingsType.GLOBAL;
    COURSE = IrisSettingsType.COURSE;
    EXERCISE = IrisSettingsType.EXERCISE;

    constructor(
        private irisSettingsService: IrisSettingsService,
        private alertService: AlertService,
        accountService: AccountService,
    ) {
        this.isAdmin = accountService.isAdmin();
    }

    ngOnInit(): void {
        this.loadIrisSettings();
    }

    ngDoCheck(): void {
        if (!isEqual(this.irisSettings, this.originalIrisSettings)) {
            this.isDirty = true;
        }
    }

    canDeactivateWarning?: string;

    canDeactivate(): boolean {
        return !this.isDirty;
    }

    loadIrisModels(): void {
        this.irisSettingsService.getIrisModels().subscribe((models) => {
            this.allIrisModels = models;
            this.isLoading = false;
        });
    }

    loadIrisSettings(): void {
        this.isLoading = true;
        this.loadIrisSettingsObservable().subscribe((settings) => {
            //this.loadIrisModels();
            this.isLoading = false;
            if (!settings) {
                this.alertService.error('artemisApp.iris.settings.error.noSettings');
            }
            this.irisSettings = settings;
            this.fillEmptyIrisSubSettings();
            this.originalIrisSettings = cloneDeep(settings);
            this.autoLectureIngestion = this.irisSettings?.irisLectureIngestionSettings?.autoIngestOnLectureAttachmentUpload ?? false;
            this.isDirty = false;
        });
        this.loadParentIrisSettingsObservable().subscribe((settings) => {
            if (!settings) {
                this.alertService.error('artemisApp.iris.settings.error.noParentSettings');
            }
            this.parentIrisSettings = settings;
            this.parentIrisEventSettings = {};
            if (settings?.irisProactivitySettings?.eventSettings) {
                settings.irisProactivitySettings.eventSettings.forEach((eventSetting) => {
                    this.parentIrisEventSettings![eventSetting.type] = eventSetting;
                });
            }
        });
    }

    fillEmptyIrisSubSettings(): void {
        if (!this.irisSettings) {
            return;
        }
        if (!this.irisSettings.irisChatSettings) {
            this.irisSettings.irisChatSettings = new IrisChatSubSettings();
        }
        if (!this.irisSettings.irisLectureIngestionSettings) {
            this.irisSettings.irisLectureIngestionSettings = new IrisLectureIngestionSubSettings();
        }
        if (!this.irisSettings.irisHestiaSettings) {
            this.irisSettings.irisHestiaSettings = new IrisHestiaSubSettings();
        }
        if (!this.irisSettings.irisCompetencyGenerationSettings) {
            this.irisSettings.irisCompetencyGenerationSettings = new IrisCompetencyGenerationSubSettings();
        }
        if (!this.irisSettings.irisProactivitySettings) {
            this.irisSettings.irisProactivitySettings = new IrisProactivitySubSettings();
        }
        if (!this.irisSettings.irisProactivitySettings.eventSettings) {
            const jolEventSettings = new JolEventSettings();
            const submissionSuccessfulEventSettings = new SubmissionSuccessfulEventSettings();
            const submissionFailedEventSettings = new SubmissionFailedEventSettings();
            this.irisSettings.irisProactivitySettings.eventSettings = [jolEventSettings, submissionSuccessfulEventSettings, submissionFailedEventSettings];
        }
    }

    saveIrisSettings(): void {
        this.isSaving = true;
        if (this.irisSettings && this.irisSettings.irisLectureIngestionSettings) {
            this.irisSettings.irisLectureIngestionSettings.autoIngestOnLectureAttachmentUpload = this.autoLectureIngestion;
        }
        this.saveIrisSettingsObservable().subscribe(
            (response) => {
                this.isSaving = false;
                this.isDirty = false;
                this.irisSettings = response.body ?? undefined;
                this.fillEmptyIrisSubSettings();
                this.originalIrisSettings = cloneDeep(this.irisSettings);
                this.alertService.success('artemisApp.iris.settings.success');
            },
            (error) => {
                this.isSaving = false;
                console.error('Error saving iris settings', error);
                if (error.status === 400 && error.error && error.error.message) {
                    this.alertService.error(error.error.message);
                } else {
                    this.alertService.error('artemisApp.iris.settings.error.save');
                }
            },
        );
    }

    loadParentIrisSettingsObservable(): Observable<IrisSettings | undefined> {
        switch (this.settingsType) {
            case IrisSettingsType.GLOBAL:
                // Global settings have no parent
                return new Observable<IrisSettings | undefined>();
            case IrisSettingsType.COURSE:
                return this.irisSettingsService.getGlobalSettings();
            case IrisSettingsType.EXERCISE:
                return this.irisSettingsService.getCombinedCourseSettings(this.courseId!);
        }
    }

    loadIrisSettingsObservable(): Observable<IrisSettings | undefined> {
        switch (this.settingsType) {
            case IrisSettingsType.GLOBAL:
                return this.irisSettingsService.getGlobalSettings();
            case IrisSettingsType.COURSE:
                return this.irisSettingsService.getUncombinedCourseSettings(this.courseId!);
            case IrisSettingsType.EXERCISE:
                return this.irisSettingsService.getUncombinedProgrammingExerciseSettings(this.exerciseId!);
        }
    }

    saveIrisSettingsObservable(): Observable<HttpResponse<IrisSettings | undefined>> {
        switch (this.settingsType) {
            case IrisSettingsType.GLOBAL:
                return this.irisSettingsService.setGlobalSettings(this.irisSettings!);
            case IrisSettingsType.COURSE:
                return this.irisSettingsService.setCourseSettings(this.courseId!, this.irisSettings!);
            case IrisSettingsType.EXERCISE:
                return this.irisSettingsService.setProgrammingExerciseSettings(this.exerciseId!, this.irisSettings!);
        }
    }

    eventSettingsChanged(eventSettings: IrisEventSettings): void {
        if (!this.irisSettings?.irisProactivitySettings) {
            return;
        }
        const eventSettingsIndex = this.irisSettings.irisProactivitySettings.eventSettings.findIndex((setting) => setting.type === eventSettings.type);
        if (eventSettingsIndex >= 0) {
            this.irisSettings.irisProactivitySettings.eventSettings[eventSettingsIndex] = eventSettings;
            this.isDirty = true;
        }
    }

    protected readonly IrisEventType = IrisEventType;
}
