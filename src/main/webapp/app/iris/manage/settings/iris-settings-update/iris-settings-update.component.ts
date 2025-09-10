import { Component, DoCheck, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { IrisSettings, IrisSettingsType } from 'app/iris/shared/entities/settings/iris-settings.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { HttpResponse } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faRotate, faSave } from '@fortawesome/free-solid-svg-icons';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { cloneDeep, isEqual } from 'lodash-es';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisCommonSubSettingsUpdateComponent } from './iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { FormsModule } from '@angular/forms';
import { captureException } from '@sentry/angular';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { IrisEmptySettingsService } from 'app/iris/manage/settings/shared/iris-empty-settings.service';

@Component({
    selector: 'jhi-iris-settings-update',
    templateUrl: './iris-settings-update.component.html',
    imports: [ButtonComponent, TranslateDirective, IrisCommonSubSettingsUpdateComponent, FormsModule],
})
export class IrisSettingsUpdateComponent implements OnInit, OnDestroy, DoCheck, ComponentCanDeactivate {
    private irisSettingsService = inject(IrisSettingsService);
    private alertService = inject(AlertService);
    private featureToggleService = inject(FeatureToggleService);
    private irisEmptySettingService = inject(IrisEmptySettingsService);

    @Input()
    public settingsType: IrisSettingsType;
    @Input()
    public courseId?: number;
    @Input()
    public exerciseId?: number;
    public irisSettings?: IrisSettings;
    public parentIrisSettings?: IrisSettings;

    originalIrisSettings?: IrisSettings;

    public autoLectureIngestion = false;
    public autoFaqIngestion = false;

    featureToggleSubscription?: Subscription;
    tutorSuggestionFeatureEnabled = false;

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

    constructor() {
        const accountService = inject(AccountService);

        this.isAdmin = accountService.isAdmin();
    }

    ngOnInit(): void {
        this.loadIrisSettings();
        this.featureToggleSubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.TutorSuggestions).subscribe((active) => {
            this.tutorSuggestionFeatureEnabled = active;
        });
    }

    ngOnDestroy(): void {
        this.featureToggleSubscription?.unsubscribe();
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

    loadIrisSettings(): void {
        this.isLoading = true;
        this.loadIrisSettingsObservable().subscribe((settings) => {
            //this.loadIrisModels();
            this.isLoading = false;
            if (!settings) {
                this.alertService.error('artemisApp.iris.settings.error.noSettings');
            }
            this.irisSettings = settings;
            this.irisSettings = this.irisEmptySettingService.fillEmptyIrisSubSettings(this.irisSettings);
            this.originalIrisSettings = cloneDeep(settings);
            this.autoLectureIngestion = this.irisSettings?.irisLectureIngestionSettings?.autoIngestOnLectureAttachmentUpload ?? true;
            this.autoFaqIngestion = this.irisSettings?.irisFaqIngestionSettings?.autoIngestOnFaqCreation ?? true;
            this.isDirty = false;
        });
        this.loadParentIrisSettingsObservable().subscribe((settings) => {
            if (!settings) {
                this.alertService.error('artemisApp.iris.settings.error.noParentSettings');
            }
            this.parentIrisSettings = settings;
        });
    }

    saveIrisSettings(): void {
        this.isSaving = true;
        if (this.irisSettings && this.irisSettings.irisLectureIngestionSettings) {
            this.irisSettings.irisLectureIngestionSettings.autoIngestOnLectureAttachmentUpload = this.autoLectureIngestion;
        }
        if (this.irisSettings && this.irisSettings.irisFaqIngestionSettings) {
            this.irisSettings.irisFaqIngestionSettings.autoIngestOnFaqCreation = this.autoFaqIngestion;
        }
        this.saveIrisSettingsObservable().subscribe({
            next: (response) => {
                this.isSaving = false;
                this.isDirty = false;
                this.irisSettings = response.body ?? undefined;
                this.irisSettings = this.irisEmptySettingService.fillEmptyIrisSubSettings(this.irisSettings);
                this.originalIrisSettings = cloneDeep(this.irisSettings);
                this.alertService.success('artemisApp.iris.settings.success');
            },
            error: (error) => {
                this.isSaving = false;
                captureException('Error saving iris settings', error);
                if (error.status === 400 && error.error && error.error.message) {
                    this.alertService.error(error.error.message);
                } else {
                    this.alertService.error('artemisApp.iris.settings.error.save');
                }
            },
        });
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
                return this.irisSettingsService.getUncombinedExerciseSettings(this.exerciseId!);
        }
    }

    saveIrisSettingsObservable(): Observable<HttpResponse<IrisSettings | undefined>> {
        switch (this.settingsType) {
            case IrisSettingsType.GLOBAL:
                return this.irisSettingsService.setGlobalSettings(this.irisSettings!);
            case IrisSettingsType.COURSE:
                return this.irisSettingsService.setCourseSettings(this.courseId!, this.irisSettings!);
            case IrisSettingsType.EXERCISE:
                return this.irisSettingsService.setExerciseSettings(this.exerciseId!, this.irisSettings!);
        }
    }
}
