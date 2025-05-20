import { Component, OnInit, inject, input } from '@angular/core';
import { IrisSubSettings, IrisSubSettingsType } from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-iris-enabled',
    templateUrl: './iris-enabled.component.html',
    imports: [TranslateDirective, NgClass, RouterLink, FaIconComponent],
    standalone: true,
})
export class IrisEnabledComponent implements OnInit {
    protected readonly faArrowRight = faArrowRight;
    private irisSettingsService = inject(IrisSettingsService);

    exercise = input<Exercise | undefined>();
    course = input<Course | undefined>();
    lecture = input<Lecture | undefined>();
    irisSubSettingsType = input.required<IrisSubSettingsType>();
    showCustomButton = input<boolean>(false);

    irisSettings?: IrisSettings;
    irisSubSettings?: IrisSubSettings;
    someButNotAllSettingsEnabled = false;

    ngOnInit(): void {
        if (this.exercise()) {
            this.irisSettingsService.getUncombinedExerciseSettings(this.exercise()!.id!).subscribe((settings) => {
                this.irisSettings = settings;
                this.setSubSettings();
            });
        } else if (this.course()) {
            this.irisSettingsService.getUncombinedCourseSettings(this.course()!.id!).subscribe((settings) => {
                this.irisSettings = settings;
                this.setSubSettings();
            });
        }
    }

    setEnabled(enabled: boolean) {
        if (this.irisSubSettings && this.irisSubSettingsType() != IrisSubSettingsType.ALL) {
            this.irisSubSettings.enabled = enabled;
            if (this.exercise()) {
                this.irisSettingsService.setExerciseSettings(this.exercise()!.id!, this.irisSettings!).subscribe((response) => {
                    this.irisSettings = response.body ?? this.irisSettings;
                    this.setSubSettings();
                });
            } else if (this.course()) {
                this.irisSettingsService.setCourseSettings(this.course()!.id!, this.irisSettings!).subscribe((response) => {
                    this.irisSettings = response.body ?? this.irisSettings;
                    this.setSubSettings();
                });
            }
        } else if (this.irisSubSettingsType() == IrisSubSettingsType.ALL && this.irisSettings && this.course()) {
            // If the subsettings type is ALL, we need to set all subsettings to the same value
            this.toggleEnabledStateForAllSubSettings(enabled);
        }
    }

    private toggleEnabledStateForAllSubSettings(enabled: boolean) {
        if (!this.irisSettings) {
            return;
        }
        if (
            !this.irisSettings.irisChatSettings ||
            !this.irisSettings.irisTextExerciseChatSettings ||
            !this.irisSettings.irisCourseChatSettings ||
            !this.irisSettings.irisCompetencyGenerationSettings ||
            !this.irisSettings.irisLectureChatSettings ||
            !this.irisSettings.irisLectureIngestionSettings ||
            !this.irisSettings.irisFaqIngestionSettings ||
            !this.irisSettings.irisTutorSuggestionSettings
        ) {
            return;
        }
        this.irisSettings.irisChatSettings.enabled = enabled;
        this.irisSettings.irisTextExerciseChatSettings.enabled = enabled;
        this.irisSettings.irisCourseChatSettings.enabled = enabled;
        this.irisSettings.irisCompetencyGenerationSettings.enabled = enabled;
        this.irisSettings.irisLectureChatSettings.enabled = enabled;
        this.irisSettings.irisFaqIngestionSettings.enabled = enabled;
        this.irisSettings.irisLectureIngestionSettings.enabled = enabled;
        this.irisSettings.irisTutorSuggestionSettings.enabled = enabled;

        this.irisSettingsService.setCourseSettings(this.course()!.id!, this.irisSettings!).subscribe((response) => {
            this.irisSettings = response.body ?? this.irisSettings;
            this.setSubSettings();
        });
    }

    private setSubSettings() {
        switch (this.irisSubSettingsType()) {
            case IrisSubSettingsType.CHAT:
                this.irisSubSettings = this.irisSettings?.irisChatSettings;
                break;
            case IrisSubSettingsType.ALL:
                this.handleSubSettingsTypeAll();
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

    private handleSubSettingsTypeAll() {
        const subSettings = [
            this.irisSettings?.irisChatSettings,
            this.irisSettings?.irisTextExerciseChatSettings,
            this.irisSettings?.irisCourseChatSettings,
            this.irisSettings?.irisCompetencyGenerationSettings,
            this.irisSettings?.irisLectureIngestionSettings,
            this.irisSettings?.irisFaqIngestionSettings,
            this.irisSettings?.irisLectureChatSettings,
            this.irisSettings?.irisTutorSuggestionSettings,
        ];

        const allEnabled = subSettings.every((settings) => settings?.enabled);
        const anyEnabled = subSettings.some((settings) => settings?.enabled);

        this.irisSubSettings = { type: IrisSubSettingsType.ALL, enabled: anyEnabled };
        this.someButNotAllSettingsEnabled = anyEnabled && !allEnabled;
    }
}
