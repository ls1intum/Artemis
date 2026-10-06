import { Component, computed, effect, input, model, output, signal, untracked } from '@angular/core';
import { TumAetUiDialogComponent, TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabsComponent } from '@tumaet/ui-angular';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

type ImportTab = 'existing' | 'file';

/**
 * Lets the instructor pick an existing exercise of the given type, or for programming exercises a file, to import into an exercise
 * group. The dialog is created while it is open, so every opening starts on the list of existing exercises with an empty search.
 */
@Component({
    selector: 'jhi-exam-exercise-import-dialog',
    templateUrl: './exam-exercise-import-dialog.component.html',
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        ExerciseImportComponent,
        ExerciseImportFromFileComponent,
        TumAetUiDialogComponent,
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
    ],
})
export class ExamExerciseImportDialogComponent {
    readonly visible = model(false);
    readonly exerciseType = input.required<ExerciseType>();
    /** Emits the exercise to import. An exercise read from a file has no id. */
    readonly imported = output<Exercise>();

    protected readonly activeTab = signal<ImportTab>('existing');
    protected readonly fromFileAvailable = computed(() => this.exerciseType() === ExerciseType.PROGRAMMING);
    protected readonly headerKey = computed(() =>
        this.exerciseType() === ExerciseType.FILE_UPLOAD ? 'artemisApp.fileUploadExercise.home.importLabel' : `artemisApp.${this.exerciseType()}Exercise.home.importLabel`,
    );

    constructor() {
        // The tab is state of this component, which outlives a single opening, so it is reset when the dialog closes.
        effect(() => {
            if (!this.visible()) {
                untracked(() => this.activeTab.set('existing'));
            }
        });
    }

    protected selectTab(value: unknown): void {
        this.activeTab.set(value === 'file' ? 'file' : 'existing');
    }
}
