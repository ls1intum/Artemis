import { TumUiButtonComponent, TumUiButtonDirective, TumUiCheckboxComponent, TumUiInputDirective, TumUiTagComponent } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AdaptFinding } from 'app/exercise/review/review-comment-utils';
import { ConsistencyIssueSeverityEnum } from 'app/openapi/model/consistency-issue';

export interface ReviewAdaptExerciseDialogResult {
    instructions?: string;
    selectedFeedbackThreadIds?: number[];
}

/** Severity order so the most important findings surface first when there are many to triage. */
const SEVERITY_ORDER: Record<string, number> = {
    [ConsistencyIssueSeverityEnum.High]: 0,
    [ConsistencyIssueSeverityEnum.Medium]: 1,
    [ConsistencyIssueSeverityEnum.Low]: 2,
};

const MAX_INSTRUCTIONS_LENGTH = 8000;

/**
 * Body of the "adapt exercise" dialog: it renders the findings the instructor picked and collects free-text
 * instructions. The host owns presentation and the result — this component only reports the decision.
 */
@Component({
    selector: 'jhi-review-adapt-exercise-dialog',
    templateUrl: './review-adapt-exercise-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        TumUiButtonDirective,
        FormsModule,
        TumUiButtonComponent,
        TumUiTagComponent,
        TumUiInputDirective,
        TumUiCheckboxComponent,
        FaIconComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
    ],
})
export class ReviewAdaptExerciseDialogComponent {
    readonly findings = input<AdaptFinding[]>([]);
    readonly selectedFeedbackThreadIds = input<number[]>();
    readonly selectedIds = linkedSignal(() => this.selectedFeedbackThreadIds() ?? []);
    protected readonly selectionRows = computed(() =>
        this.sortedFindings().map((finding) => ({
            finding,
            selected: this.selectedFeedbackThreadIds() === undefined || (finding.threadId !== undefined && this.selectedIds().includes(finding.threadId)),
        })),
    );
    protected readonly selectedCount = computed(() => this.selectionRows().filter((row) => row.selected).length);

    protected toggleFinding(threadId: number, selected: boolean): void {
        this.selectedIds.update((ids) => (selected ? [...ids, threadId] : ids.filter((id) => id !== threadId)));
    }

    readonly progressLink = input<(string | number)[]>();
    readonly blockedReason = input<string>();
    readonly submitting = input(false);
    readonly submissionError = input<string>();

    readonly confirmed = output<ReviewAdaptExerciseDialogResult>();
    readonly cancelled = output<void>();

    readonly instructions = signal('');

    protected readonly facArtemisIntelligence = facArtemisIntelligence;
    protected readonly maxInstructionsLength = MAX_INSTRUCTIONS_LENGTH;

    protected readonly sortedFindings = computed(() =>
        [...this.findings()].sort((a, b) => (a.severity ? SEVERITY_ORDER[a.severity] : 3) - (b.severity ? SEVERITY_ORDER[b.severity] : 3)),
    );
    protected readonly isFreeMode = computed(() => this.selectedCount() === 0);
    protected readonly remainingCharacters = computed(() => MAX_INSTRUCTIONS_LENGTH - this.instructions().length);
    /** Without findings there is nothing to act on, so free-form instructions become mandatory. */
    protected readonly confirmDisabled = computed(
        () => !!this.blockedReason() || this.submitting() || this.instructions().length > MAX_INSTRUCTIONS_LENGTH || (this.isFreeMode() && this.instructions().trim().length === 0),
    );

    protected confirm(): void {
        if (this.confirmDisabled()) {
            return;
        }
        const result: ReviewAdaptExerciseDialogResult = { instructions: this.instructions().trim() || undefined };
        if (this.selectedFeedbackThreadIds() !== undefined) {
            result.selectedFeedbackThreadIds = this.selectionRows()
                .filter((row) => row.selected)
                .map((row) => row.finding.threadId!)
                .filter((id) => id !== undefined);
        }
        this.confirmed.emit(result);
    }
}
