import { Component, Input, OnInit, inject } from '@angular/core';
import { IrisSubSettings, IrisSubSettingsType } from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
    selector: 'jhi-iris-enabled',
    templateUrl: './iris-enabled.component.html',
    imports: [TranslateDirective, NgClass, RouterLink],
})
export class IrisEnabledComponent implements OnInit {
    private irisSettingsService = inject(IrisSettingsService);

    @Input() exercise?: Exercise;
    @Input() course?: Course;
    @Input() lecture?: Lecture;
    @Input() irisSubSettingsType: IrisSubSettingsType;
    @Input() disabled? = false;

    irisSettings?: IrisSettings;
    irisSubSettings?: IrisSubSettings;

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
        if (!this.disabled && this.irisSubSettings && this.irisSubSettingsType != IrisSubSettingsType.ALL) {
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
        } else if (!this.disabled && this.irisSubSettingsType == IrisSubSettingsType.ALL && this.irisSettings && this.course) {
            // If the subsettings type is ALL, we need to set all subsettings to the same value

            if (!this.irisSettings.irisChatSettings) {
                this.irisSettings.irisChatSettings = { enabled, type: IrisSubSettingsType.CHAT };
            } else {
                this.irisSettings.irisChatSettings.enabled = enabled;
            }

            if (!this.irisSettings.irisTextExerciseChatSettings) {
                this.irisSettings.irisTextExerciseChatSettings = { type: IrisSubSettingsType.TEXT_EXERCISE_CHAT, enabled };
            } else {
                this.irisSettings.irisTextExerciseChatSettings.enabled = enabled;
            }

            if (!this.irisSettings.irisCourseChatSettings) {
                this.irisSettings.irisCourseChatSettings = { type: IrisSubSettingsType.COURSE_CHAT, enabled };
            } else {
                this.irisSettings.irisCourseChatSettings.enabled = enabled;
            }

            if (!this.irisSettings.irisCompetencyGenerationSettings) {
                this.irisSettings.irisCompetencyGenerationSettings = { type: IrisSubSettingsType.COMPETENCY_GENERATION, enabled };
            } else {
                this.irisSettings.irisCompetencyGenerationSettings.enabled = enabled;
            }
            if (!this.irisSettings.irisLectureChatSettings) {
                this.irisSettings.irisLectureChatSettings = { type: IrisSubSettingsType.LECTURE, enabled };
            } else {
                this.irisSettings.irisLectureChatSettings.enabled = enabled;
            }
            if (!this.irisSettings.irisFaqIngestionSettings) {
                this.irisSettings.irisFaqIngestionSettings = { type: IrisSubSettingsType.FAQ_INGESTION, enabled, autoIngestOnFaqCreation: false };
            } else {
                this.irisSettings.irisFaqIngestionSettings.enabled = enabled;
            }

            if (!this.irisSettings.irisLectureIngestionSettings) {
                this.irisSettings.irisLectureIngestionSettings = { type: IrisSubSettingsType.LECTURE_INGESTION, enabled, autoIngestOnLectureAttachmentUpload: false };
            } else {
                this.irisSettings.irisLectureIngestionSettings.enabled = enabled;
            }

            this.irisSettingsService.setCourseSettings(this.course.id!, this.irisSettings!).subscribe((response) => {
                this.irisSettings = response.body ?? this.irisSettings;
                this.setSubSettings();
            });
        }
    }

    private setSubSettings() {
        switch (this.irisSubSettingsType) {
            case IrisSubSettingsType.CHAT:
                this.irisSubSettings = this.irisSettings?.irisChatSettings;
                break;
            case IrisSubSettingsType.ALL:
                this.irisSubSettings = this.irisSettings?.irisChatSettings ? { ...this.irisSettings.irisChatSettings } : { type: IrisSubSettingsType.CHAT, enabled: false };

                this.irisSubSettings.enabled = [
                    this.irisSettings?.irisChatSettings?.enabled,
                    this.irisSettings?.irisTextExerciseChatSettings?.enabled,
                    this.irisSettings?.irisCourseChatSettings?.enabled,
                    this.irisSettings?.irisCompetencyGenerationSettings?.enabled,
                    this.irisSettings?.irisLectureIngestionSettings?.enabled,
                ].some((settingEnabled) => settingEnabled === true);
                break;
            case IrisSubSettingsType.TEXT_EXERCISE_CHAT:
                this.irisSubSettings = this.irisSettings?.irisTextExerciseChatSettings;
                break;
            case IrisSubSettingsType.COURSE_CHAT:
                this.irisSubSettings = this.irisSettings?.irisCourseChatSettings;
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
