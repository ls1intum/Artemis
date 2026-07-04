import { CommonModule } from '@angular/common';
import { Component, computed, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { parseJson } from 'app/foundation/util/json.util';

export class CourseCompetencyImportSettings {
    importRelations = false;
    importExercises = false;
    importLectures = false;
    referenceDate?: Date = undefined;
    isReleaseDate?: boolean = undefined;
}

@Component({
    selector: 'jhi-import-course-competencies-settings',
    imports: [NgbTooltipModule, FormsModule, CommonModule, FontAwesomeModule, DatePickerModule, ArtemisTranslatePipe, TranslateDirective],
    templateUrl: './import-course-competencies-settings.component.html',
    styleUrl: './import-course-competencies-settings.component.scss',
})
export class ImportCourseCompetenciesSettingsComponent {
    protected readonly faQuestionCircle = faQuestionCircle;

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

    public setReferenceDate(date?: Date | string | null): void {
        const referenceDate = date instanceof Date ? date : undefined;
        this.importSettings.update((settings) => ({
            ...settings,
            referenceDate,
            isReleaseDate: referenceDate ? (settings.referenceDate ? settings.isReleaseDate : true) : undefined,
        }));
    }

    protected setReferenceDateType(event: Event): void {
        const target = event.target as HTMLInputElement;
        this.importSettings.update((settings) => ({
            ...settings,
            isReleaseDate: parseJson<boolean>(target.value),
        }));
    }
}
