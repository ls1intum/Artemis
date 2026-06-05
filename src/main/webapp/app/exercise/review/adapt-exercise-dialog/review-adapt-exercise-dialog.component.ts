import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * Data passed into the adapt-exercise dialog.
 *
 * The dialog serves two adapt modes from a single component:
 * <ul>
 *   <li><b>Review-thread mode</b> ({@code findingText} present): a concrete consistency/verification finding is shown read-only and the
 *       free-text instructions are optional (the finding alone is enough to act on).</li>
 *   <li><b>Free mode</b> ({@code findingText} absent): there is no finding, so the finding section is hidden and the instructions become the
 *       sole input and are therefore required.</li>
 * </ul>
 */
export interface ReviewAdaptExerciseDialogData {
    /** The review thread's finding text that Artemis Intelligence will address (shown read-only). Absent in the finding-free "free adapt" mode. */
    findingText?: string;
}

/**
 * The result the dialog hands back when the instructor confirms, or {@code undefined} when cancelled.
 */
export interface ReviewAdaptExerciseDialogResult {
    /** Free-text instructions the instructor added. Optional in review-thread mode, required (non-empty) in free mode. */
    instructions?: string;
}

/**
 * Artemis Intelligence dialog that confirms adapting an exercise, with a free-text instructions field.
 *
 * The dialog is intentionally dumb: it only collects the (optional or required) instructions and closes with the result. In review-thread mode the
 * widget assembles the final feedback prompt and emits it; in free mode the host triggers the run with the bare instructions. The dialog never talks
 * to HTTP.
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

    /** The finding to address; absent in the finding-free "free adapt" mode. */
    readonly findingText: string | undefined = (this.dialogConfig.data as ReviewAdaptExerciseDialogData).findingText;
    /** Whether this is the finding-free "free adapt" mode (no finding, instructions required). */
    readonly isFreeMode = this.findingText === undefined;
    readonly instructions = signal('');
    /** In free mode the confirm action is blocked until the instructor has typed instructions; in review-thread mode the finding alone suffices. */
    readonly confirmDisabled = computed(() => this.isFreeMode && this.instructions().trim().length === 0);

    /** Closes the dialog with the instructions so the host can assemble and emit the feedback prompt (review-thread mode) or start the run (free mode). */
    confirm(): void {
        if (this.confirmDisabled()) {
            return;
        }
        const trimmed = this.instructions().trim();
        this.dialogRef.close({ instructions: trimmed || undefined } satisfies ReviewAdaptExerciseDialogResult);
    }

    /** Closes the dialog without adapting. */
    cancel(): void {
        this.dialogRef.close(undefined);
    }
}
