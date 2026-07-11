import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { TextareaModule } from 'primeng/textarea';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AdaptFinding } from 'app/exercise/review/review-comment-utils';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';

export interface ReviewAdaptExerciseDialogData {
    findings?: AdaptFinding[];
}

export interface ReviewAdaptExerciseDialogResult {
    instructions?: string;
}

@Component({
    selector: 'jhi-review-adapt-exercise-dialog',
    templateUrl: './review-adapt-exercise-dialog.component.html',
    styleUrl: './review-adapt-exercise-dialog.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ButtonModule, TagModule, TextareaModule, MessageModule, FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
})
export class ReviewAdaptExerciseDialogComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    protected readonly facArtemisIntelligence = facArtemisIntelligence;
    protected readonly maxInstructionsLength = 8000;

    /** Severity order so the most important findings surface first when there are many to triage. */
    private static readonly SEVERITY_ORDER: Record<string, number> = {
        [ConsistencyIssue.SeverityEnum.High]: 0,
        [ConsistencyIssue.SeverityEnum.Medium]: 1,
        [ConsistencyIssue.SeverityEnum.Low]: 2,
    };

    readonly findings: AdaptFinding[] = [...((this.dialogConfig.data as ReviewAdaptExerciseDialogData | undefined)?.findings ?? [])].sort(
        (a, b) => ReviewAdaptExerciseDialogComponent.SEVERITY_ORDER[a.severity] - ReviewAdaptExerciseDialogComponent.SEVERITY_ORDER[b.severity],
    );
    readonly isFreeMode = this.findings.length === 0;
    readonly instructions = signal('');
    readonly remainingCharacters = computed(() => this.maxInstructionsLength - this.instructions().length);

    readonly confirmDisabled = computed(() => this.isFreeMode && this.instructions().trim().length === 0);

    confirm(): void {
        if (this.confirmDisabled()) {
            return;
        }
        const trimmed = this.instructions().trim();
        this.dialogRef.close({ instructions: trimmed || undefined } satisfies ReviewAdaptExerciseDialogResult);
    }

    cancel(): void {
        this.dialogRef.close(undefined);
    }
}
