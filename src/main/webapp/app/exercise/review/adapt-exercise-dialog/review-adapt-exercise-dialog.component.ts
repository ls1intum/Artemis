import { TumUiButtonComponent, TumUiInputDirective, TumUiMessageComponent, TumUiTagComponent } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AdaptFinding } from 'app/exercise/review/review-comment-utils';
import { ConsistencyIssueSeverityEnum } from 'app/openapi/model/consistency-issue';

export interface ReviewAdaptExerciseDialogResult {
    instructions?: string;
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
    imports: [FormsModule, TumUiButtonComponent, TumUiTagComponent, TumUiInputDirective, TumUiMessageComponent, FaIconComponent, ArtemisTranslatePipe, TranslateDirective],
})
export class ReviewAdaptExerciseDialogComponent {
    readonly findings = input<AdaptFinding[]>([]);

    readonly confirmed = output<ReviewAdaptExerciseDialogResult>();
    readonly cancelled = output<void>();

    readonly instructions = signal('');

    protected readonly facArtemisIntelligence = facArtemisIntelligence;
    protected readonly maxInstructionsLength = MAX_INSTRUCTIONS_LENGTH;

    protected readonly sortedFindings = computed(() => [...this.findings()].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]));
    protected readonly isFreeMode = computed(() => this.sortedFindings().length === 0);
    protected readonly remainingCharacters = computed(() => MAX_INSTRUCTIONS_LENGTH - this.instructions().length);
    /** Without findings there is nothing to act on, so free-form instructions become mandatory. */
    protected readonly confirmDisabled = computed(() => this.isFreeMode() && this.instructions().trim().length === 0);

    protected confirm(): void {
        if (this.confirmDisabled()) {
            return;
        }
        this.confirmed.emit({ instructions: this.instructions().trim() || undefined });
    }
}
