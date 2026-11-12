import { Component, computed, input } from '@angular/core';
import { Feedback, FeedbackType, instructionCountsOf } from 'app/assessment/shared/entities/feedback.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/** Explains the scoring cap on every feedback sharing an overused instruction, without blocking its use. */
@Component({
    selector: 'jhi-grading-instruction-usage-warning',
    imports: [TranslateDirective],
    template: `
        @if (!readOnly() && usage(); as instructionUsage) {
            <p
                class="text-state-info text-sm mt-2"
                data-testid="grading-instruction-usage-warning"
                jhiTranslate="artemisApp.assessment.gradingInstructionUsageWarning"
                [translateValues]="instructionUsage"
            ></p>
        }
    `,
})
export class GradingInstructionUsageWarningComponent {
    readonly feedback = input.required<Feedback>();
    readonly allFeedbacks = input<Feedback[]>([]);
    readonly readOnly = input(false);
    readonly capAutomaticTestSubtotal = input(false);

    readonly usage = computed(() => {
        const instruction = this.feedback()?.gradingInstruction;
        const feedbacks = this.capAutomaticTestSubtotal()
            ? this.allFeedbacks().filter((feedback) => feedback.type !== FeedbackType.AUTOMATIC || Feedback.isStaticCodeAnalysisFeedback(feedback))
            : this.allFeedbacks();
        const limit = instruction?.usageCount ?? 0;
        if (instruction?.id === undefined || limit <= 0) {
            return undefined;
        }
        const count = instructionCountsOf(feedbacks).get(instruction.id) ?? 0;
        return count > limit ? { count, limit } : undefined;
    });
}
