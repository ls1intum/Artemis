import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { Component, inject, input, signal } from '@angular/core';
import { faBan, faCheck, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiDialogRef } from '@tumaet/ui-angular';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-exercise-update-warning',
    templateUrl: './exercise-update-warning.component.html',
    styleUrls: ['./exercise-update-warning.component.scss'],
    imports: [TumAetUiCheckboxComponent, TumAetUiButtonDirective, TranslateDirective, FormsModule, FaIconComponent],
})
export class ExerciseUpdateWarningComponent {
    private dialogRef = inject(TumAetUiDialogRef, { optional: true });

    readonly instructionDeleted = input(false);
    readonly creditChanged = input(false);
    readonly deleteFeedback = signal(false);
    readonly usageCountChanged = input(false);
    readonly immediateReleaseWarning = input('');

    confirmed = new Subject<void>();
    reEvaluated = new Subject<void>();
    canceled = new Subject<void>();

    // Icons
    faBan = faBan;
    faCheck = faCheck;
    faExclamationTriangle = faExclamationTriangle;

    constructor() {
        // A shell dismissal (Escape) cancels the pending save just like the cancel button.
        // The dialog completes this stream when it closes.
        this.dialogRef?.dismissed?.subscribe(() => this.canceled.next());
    }

    /**
     * Closes the modal
     */
    clear(): void {
        this.canceled.next();
        this.closeDialog();
    }

    /**
     * Save changes without re-evaluation
     */
    saveExerciseWithoutReevaluation(): void {
        this.confirmed.next();
        this.closeDialog();
    }

    /**
     * Re-evaluate the exercise
     */
    reEvaluateExercise(): void {
        this.reEvaluated.next();
        this.closeDialog();
    }

    /**
     * Toggle the option to deleteFeedback
     */
    toggleDeleteFeedback() {
        this.deleteFeedback.update((value) => !value);
    }

    private closeDialog(): void {
        this.dialogRef?.close();
    }
}
