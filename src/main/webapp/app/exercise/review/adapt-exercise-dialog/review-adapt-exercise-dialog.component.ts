import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * Data passed into the adapt-exercise dialog by the review comment thread widget.
 */
export interface ReviewAdaptExerciseDialogData {
    /** The review thread's finding text that Artemis Intelligence will address (shown read-only). */
    findingText: string;
}

/**
 * The result the dialog hands back when the instructor confirms, or {@code undefined} when cancelled.
 */
export interface ReviewAdaptExerciseDialogResult {
    /** Optional free-text instructions the instructor added on top of the finding. */
    instructions?: string;
}

/**
 * Artemis Intelligence dialog that confirms adapting an exercise to address a review finding, with an optional free-text instructions field.
 *
 * The dialog is intentionally dumb: it only collects the optional instructions and closes with the result. The widget assembles the final feedback
 * prompt and emits it; the host triggers the run. The dialog never talks to HTTP.
 */
@Component({
    selector: 'jhi-review-adapt-exercise-dialog',
    templateUrl: './review-adapt-exercise-dialog.component.html',
    styleUrl: './review-adapt-exercise-dialog.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ButtonModule, TextareaModule, FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
})
export class ReviewAdaptExerciseDialogComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    protected readonly facArtemisIntelligence = facArtemisIntelligence;

    readonly findingText: string = (this.dialogConfig.data as ReviewAdaptExerciseDialogData).findingText;
    readonly instructions = signal('');

    /** Closes the dialog with the optional instructions so the widget can assemble and emit the feedback prompt. */
    confirm(): void {
        const trimmed = this.instructions().trim();
        this.dialogRef.close({ instructions: trimmed || undefined } satisfies ReviewAdaptExerciseDialogResult);
    }

    /** Closes the dialog without adapting. */
    cancel(): void {
        this.dialogRef.close(undefined);
    }
}
