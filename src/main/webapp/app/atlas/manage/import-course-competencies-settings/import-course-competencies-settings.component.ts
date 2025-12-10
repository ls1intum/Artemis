import { CommonModule } from '@angular/common';
import { Component, computed, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faCalendarAlt, faCircleXmark, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

export class CourseCompetencyImportSettings {
    importRelations = false;
    importExercises = false;
    importLectures = false;
    referenceDate?: Date = undefined;
    isReleaseDate?: boolean = undefined;
}

@Component({
    selector: 'jhi-import-course-competencies-settings',
    imports: [NgbTooltipModule, FormsModule, CommonModule, FontAwesomeModule, OwlDateTimeModule, OwlNativeDateTimeModule, ArtemisTranslatePipe, TranslateDirective],
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
        this.importSettings.update((settings) => Object.assign({}, settings, { [setting]: !settings[setting] }));
    }

    public setReferenceDate(dateEvent?: HTMLInputElement): void {
        this.importSettings.update((settings) =>
            Object.assign({}, settings, {
                referenceDate: dateEvent ? new Date(dateEvent.value) : undefined,
                isReleaseDate: dateEvent ? (settings.referenceDate ? settings.isReleaseDate : true) : undefined,
            }),
        );
    }

    protected setReferenceDateType(event: Event): void {
        const target = event.target as HTMLInputElement;
        this.importSettings.update((settings) => Object.assign({}, settings, { isReleaseDate: JSON.parse(target.value) }));
    }
}
