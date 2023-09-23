import { Component, Input, OnInit } from '@angular/core';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { ButtonType } from 'app/shared/components/button.component';
import { faRotate, faSave } from '@fortawesome/free-solid-svg-icons';
import { IrisSubSettings } from 'app/entities/iris/settings/iris-sub-settings.model';
import { IrisModel } from 'app/entities/iris/settings/iris-model';

export enum IrisSettingsType {
    GLOBAL = 'GLOBAL',
    COURSE = 'COURSE',
    PROGRAMMING_EXERCISE = 'PROGRAMMING_EXERCISE',
}

@Component({
    selector: 'jhi-iris-settings-update',
    templateUrl: './iris-settings-update.component.html',
})
export class IrisSettingsUpdateComponent implements OnInit {
    @Input()
    public settingType: IrisSettingsType;
    @Input()
    public courseId?: number;
    @Input()
    public programmingExerciseId?: number;

    public irisSettings?: IrisSettings;
    public irisModels?: IrisModel[];

    // Loading bools
    isLoading = false;
    isSaving = false;
    // Button types
    PRIMARY = ButtonType.PRIMARY;
    SUCCESS = ButtonType.SUCCESS;
    // Icons
    faSave = faSave;
    faRotate = faRotate;
    // Settings types
    GLOBAL = IrisSettingsType.GLOBAL;
    PROGRAMMING_EXERCISE = IrisSettingsType.PROGRAMMING_EXERCISE;

    constructor(
        private irisSettingsService: IrisSettingsService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.loadIrisSettings();
    }

    loadIrisModels(): void {
        this.irisSettingsService.getIrisModels().subscribe((models) => {
            this.irisModels = models;
            this.isLoading = false;
        });
    }

    loadIrisSettings(): void {
        this.isLoading = true;
        this.loadIrisSettingsObservable().subscribe((settings) => {
            this.loadIrisModels();
            if (!settings) {
                this.alertService.error('artemisApp.iris.settings.error.noSettings');
            }
            this.irisSettings = settings;
        });
    }

    saveIrisSettings(): void {
        this.isSaving = true;
        this.saveIrisSettingsObservable().subscribe(
            (response) => {
                this.isSaving = false;
                this.irisSettings = response.body ?? undefined;
                this.alertService.success('artemisApp.iris.settings.success');
            },
            () => {
                this.isSaving = false;
                this.alertService.error('artemisApp.iris.settings.error.save');
            },
        );
    }

    loadIrisSettingsObservable(): Observable<IrisSettings | undefined> {
        switch (this.settingType) {
            case IrisSettingsType.GLOBAL:
                return this.irisSettingsService.getGlobalSettings();
            case IrisSettingsType.COURSE:
                return this.irisSettingsService.getUncombinedCourseSettings(this.courseId!);
            case IrisSettingsType.PROGRAMMING_EXERCISE:
                return this.irisSettingsService.getUncombinedProgrammingExerciseSettings(this.programmingExerciseId!);
        }
    }

    saveIrisSettingsObservable(): Observable<HttpResponse<IrisSettings | undefined>> {
        switch (this.settingType) {
            case IrisSettingsType.GLOBAL:
                return this.irisSettingsService.setGlobalSettings(this.irisSettings!);
            case IrisSettingsType.COURSE:
                return this.irisSettingsService.setCourseSettings(this.courseId!, this.irisSettings!);
            case IrisSettingsType.PROGRAMMING_EXERCISE:
                return this.irisSettingsService.setProgrammingExerciseSettings(this.programmingExerciseId!, this.irisSettings!);
        }
    }

    onInheritHestiaSettingsChanged() {
        if (this.irisSettings?.irisHestiaSettings) {
            this.irisSettings!.irisHestiaSettings = undefined;
        } else {
            const irisSubSettings = new IrisSubSettings();
            irisSubSettings.enabled = true;
            this.irisSettings!.irisHestiaSettings = irisSubSettings;
        }
    }
}
