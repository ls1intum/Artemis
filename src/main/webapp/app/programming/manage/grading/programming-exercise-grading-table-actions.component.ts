import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { faCopy } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { GradingTab } from 'app/programming/manage/grading/programming-exercise-configure-grading.component';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
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
    private modalService = inject(NgbModal);

    readonly faCopy = faCopy;
    @Input() exercise: ProgrammingExercise;
    @Input() hasUnsavedChanges: boolean;
    @Input() isSaving: boolean;
    @Input() activeTab: GradingTab;

    @Output() onSave = new EventEmitter();
    @Output() onReset = new EventEmitter();
    @Output() onCategoryImport = new EventEmitter<number>();

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        modalRef.componentInstance.programmingLanguage = this.exercise.programmingLanguage;
        modalRef.result.then((result: ProgrammingExercise) => this.onCategoryImport.emit(result.id));
    }
}
