import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { faCopy } from '@fortawesome/free-solid-svg-icons';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { GradingTab } from 'app/programming/manage/grading/configure/programming-exercise-configure-grading.component';
import { ExerciseImportComponent, ExerciseImportDialogData } from 'app/exercise/import/exercise-import.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * The actions of the test case table:
 * - Save the test cases with the updated values.
 * - Reset all weights to 1.
 */
@Component({
    selector: 'jhi-programming-exercise-grading-table-actions',
    templateUrl: './programming-exercise-grading-table-actions.component.html',
    imports: [FaIconComponent, TranslateDirective],
})
export class ProgrammingExerciseGradingTableActionsComponent {
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);

    readonly faCopy = faCopy;
    @Input() exercise: ProgrammingExercise;
    @Input() hasUnsavedChanges: boolean;
    @Input() isSaving: boolean;
    @Input() activeTab: GradingTab;

    @Output() onSave = new EventEmitter();
    @Output() onReset = new EventEmitter();
    @Output() onCategoryImport = new EventEmitter<number>();

    openImportModal() {
        const dialogData: ExerciseImportDialogData = {
            exerciseType: ExerciseType.PROGRAMMING,
            programmingLanguage: this.exercise.programmingLanguage,
        };
        const dialogRef = this.dialogService.open(ExerciseImportComponent, {
            header: this.translateService.instant('artemisApp.programmingExercise.configureGrading.categories.importLabel'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: dialogData,
        });

        dialogRef?.onClose.subscribe((result: ProgrammingExercise | undefined) => {
            if (result?.id) {
                this.onCategoryImport.emit(result.id);
            }
        });
    }
}
