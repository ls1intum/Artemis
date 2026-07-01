import { Component, OnInit, inject, input, model, output } from '@angular/core';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    templateUrl: './unreferenced-feedback-detail.component.html',
    styleUrls: ['./unreferenced-feedback-detail.component.scss'],
    imports: [UnifiedFeedbackComponent],
})
export class UnreferencedFeedbackDetailComponent implements OnInit {
    structuredGradingCriterionService = inject(StructuredGradingCriterionService);

    public readonly feedback = model.required<Feedback>();
    readonly resultId = input.required<number>();
    readonly isSuggestion = input<boolean>();
    public readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input<boolean>(false);
    readonly useDefaultFeedbackSuggestionBadgeText = input.required<boolean>();

    public readonly onFeedbackChange = output<Feedback>();
    public readonly onFeedbackDelete = output<Feedback>();
    readonly onAcceptSuggestion = output<Feedback>();
    readonly onDiscardSuggestion = output<Feedback>();
    private feedbackService = inject(FeedbackService);

    ngOnInit() {
        this.loadLongFeedback();
    }

    /**
     * Call this method to load long feedback if needed
     */
    public async loadLongFeedback() {
        const feedback = this.feedback();
        if (feedback.id && feedback.hasLongFeedbackText) {
            feedback.detailText = await this.feedbackService.getLongFeedbackText(feedback.id);
            this.feedback.set(feedback);
            this.onFeedbackChange.emit(feedback);
        }
    }

    /**
     * Emits assessment changes to parent component
     */
    public emitChanges(): void {
        const feedback = this.feedback();
        if (feedback.type === FeedbackType.AUTOMATIC) {
            feedback.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
        Feedback.updateFeedbackTypeOnChange(feedback);
        this.feedback.set(feedback);
        this.onFeedbackChange.emit(feedback);
    }

    /**
     * Emits the deletion of a feedback
     */
    public delete() {
        this.onFeedbackDelete.emit(this.feedback());
    }

    updateFeedbackOnDrop(event: Event) {
        event.stopPropagation();
        const feedback = this.feedback();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(feedback, event);
        this.feedback.set(feedback);
        this.onFeedbackChange.emit(feedback);
    }
}
