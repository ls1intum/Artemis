import { Component, EventEmitter, inject } from '@angular/core';
import { faBan, faCheck, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

@Component({
    selector: 'jhi-exercise-update-warning',
    templateUrl: './exercise-update-warning.component.html',
    styleUrls: ['./exercise-update-warning.component.scss'],
    imports: [TranslateDirective, FormsModule, FaIconComponent],
})
export class ExerciseUpdateWarningComponent {
    private activeModal = inject(NgbActiveModal, { optional: true });
    private dialogRef = inject(DynamicDialogRef, { optional: true });

    instructionDeleted = false;
    creditChanged = false;
    deleteFeedback = false;
    usageCountChanged = false;
    immediateReleaseWarning = '';

    confirmed = new EventEmitter<void>();
    reEvaluated = new EventEmitter<void>();
    canceled = new EventEmitter<void>();

    // Icons
    faBan = faBan;
    faCheck = faCheck;
    faExclamationTriangle = faExclamationTriangle;

    /**
     * Closes the modal
     */
    clear(): void {
        this.canceled.emit();
        this.closeDialog();
    }

    /**
     * Save changes without re-evaluation
     */
    saveExerciseWithoutReevaluation(): void {
        this.confirmed.emit();
        this.closeDialog();
    }

    /**
     * Re-evaluate the exercise
     */
    reEvaluateExercise(): void {
        this.reEvaluated.emit();
        this.closeDialog();
    }

    /**
     * Toggle the option to deleteFeedback
     */
    toggleDeleteFeedback() {
        this.deleteFeedback = !this.deleteFeedback;
    }

    private closeDialog(): void {
        this.dialogRef?.close();
        this.activeModal?.close();
    }
}
