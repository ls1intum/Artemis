import { Component, Input, OnInit } from '@angular/core';
import { IrisSubSettings, IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { Exercise } from 'app/entities/exercise.model';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { Course } from 'app/entities/course.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';

@Component({
    selector: 'jhi-iris-enabled',
    templateUrl: './iris-enabled.component.html',
})
export class IrisEnabledComponent implements OnInit {
    @Input() exercise?: Exercise;
    @Input() course?: Course;
    @Input() irisSubSettingsType: IrisSubSettingsType;
    @Input() disabled? = false;

    irisSettings?: IrisSettings;
    irisSubSettings?: IrisSubSettings;

    constructor(private irisSettingsService: IrisSettingsService) {}

    ngOnInit(): void {
        if (this.exercise) {
            this.irisSettingsService.getUncombinedExerciseSettings(this.exercise.id!).subscribe((settings) => {
                this.irisSettings = settings;
                this.setSubSettings();
            });
        } else if (this.course) {
            this.irisSettingsService.getUncombinedCourseSettings(this.course.id!).subscribe((settings) => {
                this.irisSettings = settings;
                this.setSubSettings();
            });
        }
    }

    setEnabled(enabled: boolean) {
        if (!this.disabled && this.irisSubSettings) {
            this.irisSubSettings.enabled = enabled;
            if (this.exercise) {
                this.irisSettingsService.setExerciseSettings(this.exercise.id!, this.irisSettings!).subscribe((response) => {
                    this.irisSettings = response.body ?? this.irisSettings;
                    this.setSubSettings();
                });
            } else if (this.course) {
                this.irisSettingsService.setCourseSettings(this.course.id!, this.irisSettings!).subscribe((response) => {
                    this.irisSettings = response.body ?? this.irisSettings;
                    this.setSubSettings();
                });
            }
        }
    }

    private setSubSettings() {
        switch (this.irisSubSettingsType) {
            case IrisSubSettingsType.CHAT:
                this.irisSubSettings = this.irisSettings?.irisChatSettings;
                break;
            case IrisSubSettingsType.COMPETENCY_GENERATION:
                this.irisSubSettings = this.irisSettings?.irisCompetencyGenerationSettings;
                break;
            case IrisSubSettingsType.LECTURE_INGESTION:
                this.irisSubSettings = this.irisSettings?.irisLectureIngestionSettings;
                break;
        }
    }
}
