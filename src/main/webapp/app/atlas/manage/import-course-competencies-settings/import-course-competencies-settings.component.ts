import { CommonModule } from '@angular/common';
import { Component, computed, model } from '@angular/core';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { Dayjs } from 'dayjs/esm';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { parseJson } from 'app/foundation/util/json.util';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export class CourseCompetencyImportSettings {
    importRelations = false;
    importExercises = false;
    importLectures = false;
    referenceDate?: Dayjs = undefined;
    isReleaseDate?: boolean = undefined;
}

@Component({
    selector: 'jhi-import-course-competencies-settings',
    imports: [NgbTooltipModule, CommonModule, FontAwesomeModule, TumAetUiDatePickerComponent, ArtemisTranslatePipe, TranslateDirective],
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
        this.importSettings.update((settings) => cloneWith(settings, { [setting]: !settings[setting] }));
    }

    public setReferenceDate(date?: Dayjs): void {
        const referenceDate = date?.isValid() ? date : undefined;
        this.importSettings.update((settings) =>
            cloneWith(settings, { referenceDate, isReleaseDate: referenceDate ? (settings.referenceDate ? settings.isReleaseDate : true) : undefined }),
        );
    }

    protected setReferenceDateType(event: Event): void {
        const target = event.target as HTMLInputElement;
        this.importSettings.update((settings) => cloneWith(settings, { isReleaseDate: parseJson<boolean>(target.value) }));
    }
}
