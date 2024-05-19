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
import { IrisChatSubSettings, IrisCompetencyGenerationSubSettings, IrisHestiaSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { AccountService } from 'app/core/auth/account.service';

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

    originalIrisSettings?: IrisSettings;

    // Status bools
    isLoading = false;
    isSaving = false;
    isDirty = false;
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
    isAdmin: boolean;
    lectureChat: boolean = false;

    constructor(
        private irisSettingsService: IrisSettingsService,
        private alertService: AlertService,
        private accountService: AccountService,
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
            console.log('FIRST INITIAL settings', this.irisSettings);
            this.irisSettings = settings;
            console.log('FIRST INITIAL irisSettings', this.irisSettings);
            this.lectureChat = this.irisSettings?.irisChatSettings?.lectureChat ?? false;
            console.log('FIRST INITIAL D', this.lectureChat);
            this.fillEmptyIrisSubSettings();
            this.originalIrisSettings = cloneDeep(settings);
            this.isDirty = false;
        });
        this.loadParentIrisSettingsObservable().subscribe((settings) => {
            if (!settings) {
                this.alertService.error('artemisApp.iris.settings.error.noParentSettings');
            }
            this.parentIrisSettings = settings;
        });
    }

    fillEmptyIrisSubSettings(): void {
        if (!this.irisSettings) {
            return;
        }
        if (!this.irisSettings.irisChatSettings) {
            this.irisSettings.irisChatSettings = new IrisChatSubSettings();
        }
        if (!this.irisSettings.irisHestiaSettings) {
            this.irisSettings.irisHestiaSettings = new IrisHestiaSubSettings();
        }
        if (!this.irisSettings.irisCompetencyGenerationSettings) {
            this.irisSettings.irisCompetencyGenerationSettings = new IrisCompetencyGenerationSubSettings();
        }
    }

    saveIrisSettings(): void {
        console.log('no#########################\n', this.irisSettings?.irisChatSettings?.lectureChat);
        this.syncLectureChatIngestion();
        this.isSaving = true;
        this.saveIrisSettingsObservable().subscribe(
            (response) => {
                this.isSaving = false;
                this.isDirty = false;
                console.log(response.body);
                this.irisSettings = response.body ?? undefined;
                this.fillEmptyIrisSubSettings();
                this.originalIrisSettings = cloneDeep(this.irisSettings);
                this.alertService.success('artemisApp.iris.settings.success');
            },
            () => {
                this.isSaving = false;
                this.alertService.error('artemisApp.iris.settings.error.save');
            },
        );
        console.log('yaaaas#########################\n', this.irisSettings?.irisChatSettings?.lectureChat);
    }

    saveIrisSettingsObservable(): Observable<HttpResponse<IrisSettings | undefined>> {
        switch (this.settingsType) {
            case IrisSettingsType.GLOBAL:
                return this.irisSettingsService.setGlobalSettings(this.irisSettings!);
            case IrisSettingsType.COURSE:
                console.log(' this.irisSettingsService.setCourseSettings(this.courseId!, this.irisSettings!);');
                return this.irisSettingsService.setCourseSettings(this.courseId!, this.irisSettings!);
            case IrisSettingsType.EXERCISE:
                return this.irisSettingsService.setProgrammingExerciseSettings(this.exerciseId!, this.irisSettings!);
        }
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

    private syncLectureChatIngestion() {
        if (this.irisSettings?.irisChatSettings) {
            this.irisSettings.irisChatSettings.lectureChat = this.lectureChat;
        }
    }
}
