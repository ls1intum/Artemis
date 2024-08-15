import { CommonModule } from '@angular/common';
import { Component, computed, model } from '@angular/core';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCalendarAlt, faCircleXmark, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FormsModule } from 'app/forms/forms.module';

export class CourseCompetencyImportSettings {
    importRelations = false;
    importExercises = false;
    importLectures = false;
    referenceDate?: Date = undefined;
    isReleaseDate?: boolean = undefined;
}

@Component({
    selector: 'jhi-import-course-competencies-settings',
    standalone: true,
    imports: [FormDateTimePickerModule, FormsModule, CommonModule, FontAwesomeModule, OwlDateTimeModule, OwlNativeDateTimeModule],
    templateUrl: './import-course-competencies-settings.component.html',
    styleUrl: './import-course-competencies-settings.component.scss',
})
export class ImportCourseCompetenciesSettingsComponent {
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faCalendarAlt = faCalendarAlt;
    protected readonly faCircleXmark = faCircleXmark;

    readonly importSettings = model.required<CourseCompetencyImportSettings>();
    readonly importRelations = computed(() => this.importSettings().importRelations);
    readonly importExercises = computed(() => this.importSettings().importExercises);
    readonly importLectures = computed(() => this.importSettings().importLectures);
    readonly referenceDate = computed(() => this.importSettings().referenceDate);
    readonly isReleaseDate = computed(() => this.importSettings().isReleaseDate);

    protected toggleImportSetting(setting: keyof CourseCompetencyImportSettings): void {
        this.importSettings.update((settings) => ({
            ...settings,
            [setting]: !settings[setting],
        }));
    }

    protected setReferenceDate(dateEvent: any): void {
        this.importSettings.update((settings) => ({
            ...settings,
            referenceDate: dateEvent ? new Date(dateEvent.value) : undefined,
            isReleaseDate: dateEvent ? (settings.referenceDate ? settings.isReleaseDate : true) : undefined,
        }));
    }

    protected setReferenceDateType(event: Event): void {
        const target = event.target as HTMLInputElement;
        this.importSettings.update((settings) => ({
            ...settings,
            isReleaseDate: JSON.parse(target.value),
        }));
    }
}
