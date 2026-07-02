import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AdaptFinding } from 'app/exercise/review/review-comment-utils';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';

/** Data passed into the adapt-exercise dialog (see {@code isFreeMode}/{@code confirmDisabled} for the two-mode behavior). */
export interface ReviewAdaptExerciseDialogData {
    /** The structured review-comment findings to address, shown read-only as cards. Absent/empty in the finding-free "free adapt" mode. */
    findings?: AdaptFinding[];
}

/** The result the dialog hands back when the instructor confirms, or {@code undefined} when cancelled. */
export interface ReviewAdaptExerciseDialogResult {
    /** Free-text instructions the instructor added. Optional in review-thread mode, required (non-empty) in free mode. */
    instructions?: string;
}

/** Artemis Intelligence dialog that collects optional/required instructions and closes with the result; the host assembles and starts the run. */
@Component({
    selector: 'jhi-review-adapt-exercise-dialog',
    templateUrl: './review-adapt-exercise-dialog.component.html',
    styleUrl: './review-adapt-exercise-dialog.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ButtonModule, TagModule, TextareaModule, FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
})
export class ReviewAdaptExerciseDialogComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    protected readonly facArtemisIntelligence = facArtemisIntelligence;

    /** Severity order so the most important findings surface first when there are many to triage. */
    private static readonly SEVERITY_ORDER: Record<string, number> = {
        [ConsistencyIssue.SeverityEnum.High]: 0,
        [ConsistencyIssue.SeverityEnum.Medium]: 1,
        [ConsistencyIssue.SeverityEnum.Low]: 2,
    };

    /** The structured review-comment findings to address (rendered read-only as cards, highest severity first); empty in the finding-free "free adapt" mode (no open review comments). */
    readonly findings: AdaptFinding[] = [...((this.dialogConfig.data as ReviewAdaptExerciseDialogData).findings ?? [])].sort(
        (a, b) => (ReviewAdaptExerciseDialogComponent.SEVERITY_ORDER[a.severity] ?? 3) - (ReviewAdaptExerciseDialogComponent.SEVERITY_ORDER[b.severity] ?? 3),
    );
    /** Whether this is the finding-free "free adapt" mode (no review comments, instructions required). */
    readonly isFreeMode = this.findings.length === 0;
    readonly instructions = signal('');

    /** Maps a finding severity to its PrimeNG tag severity for the coloured tag (High → danger, Medium → warn, Low → info). */
    protected severityTag(severity: AdaptFinding['severity']): 'danger' | 'warn' | 'info' {
        switch (severity) {
            case ConsistencyIssue.SeverityEnum.High:
                return 'danger';
            case ConsistencyIssue.SeverityEnum.Medium:
                return 'warn';
            default:
                return 'info';
        }
    }
    /** In free mode the confirm action is blocked until the instructor has typed instructions; with review comments to address the comments alone suffice. */
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
