import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, model, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TumAetUiCheckboxComponent, TumAetUiDatePickerComponent, TumAetUiSelectComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { Dayjs } from 'dayjs/esm';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
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
    imports: [
        NgTemplateOutlet,
        FormsModule,
        FontAwesomeModule,
        TumAetUiCheckboxComponent,
        TumAetUiDatePickerComponent,
        TumAetUiSelectComponent,
        TumAetUiTooltipDirective,
        ArtemisTranslatePipe,
        TranslateDirective,
    ],
    templateUrl: './import-course-competencies-settings.component.html',
})
export class ImportCourseCompetenciesSettingsComponent {
    private readonly translateService = inject(TranslateService);
    private readonly languageChange = toSignal(this.translateService.onLangChange, { initialValue: undefined });

    protected readonly faQuestionCircle = faQuestionCircle;

    readonly importSettings = model.required<CourseCompetencyImportSettings>();
    /** Emits whether the text of the reference date field is a date or empty; the picker keeps its last valid date while it is not. */
    readonly referenceDateTextValidChange = output<boolean>();
    readonly importRelations = computed(() => this.importSettings().importRelations);
    readonly importExercises = computed(() => this.importSettings().importExercises);
    readonly importLectures = computed(() => this.importSettings().importLectures);
    readonly referenceDate = computed(() => this.importSettings().referenceDate);
    readonly isReleaseDate = computed(() => this.importSettings().isReleaseDate);

    /** What the reference date stands for, translated again when the language changes. */
    protected readonly referenceDateTypeOptions = computed(() => {
        this.languageChange();
        return [
            { value: true, label: this.translateService.instant('artemisApp.courseCompetency.importSettings.referenceDateType.releaseDate') },
            { value: false, label: this.translateService.instant('artemisApp.courseCompetency.importSettings.referenceDateType.dueDate') },
        ];
    });

    protected toggleImportSetting(setting: keyof CourseCompetencyImportSettings): void {
        this.importSettings.update((settings) => cloneWith(settings, { [setting]: !settings[setting] }));
    }

    public setReferenceDate(date?: Dayjs): void {
        const referenceDate = date?.isValid() ? date : undefined;
        this.importSettings.update((settings) =>
            cloneWith(settings, { referenceDate, isReleaseDate: referenceDate ? (settings.referenceDate ? settings.isReleaseDate : true) : undefined }),
        );
    }

    protected setReferenceDateType(isReleaseDate: boolean | null | undefined): void {
        this.importSettings.update((settings) => cloneWith(settings, { isReleaseDate: isReleaseDate ?? undefined }));
    }
}
