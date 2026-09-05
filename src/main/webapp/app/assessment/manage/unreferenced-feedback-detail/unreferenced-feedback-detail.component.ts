import { Component, OnInit, inject, input, model, output } from '@angular/core';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

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
    public readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input<boolean>(false);

    public readonly onFeedbackChange = output<Feedback>();
    public readonly onFeedbackDelete = output<Feedback>();
    private feedbackService = inject(FeedbackService);

    ngOnInit() {
        void this.loadLongFeedback();
    }

    /**
     * Call this method to load long feedback if needed
     */
    public async loadLongFeedback() {
        const feedback = this.feedback();
        if (feedback.id && feedback.hasLongFeedbackText) {
            const detailText = await this.feedbackService.getLongFeedbackText(feedback.id);
            const updatedFeedback = cloneWith(feedback, { detailText, hasLongFeedbackText: false });
            this.feedback.set(updatedFeedback);
            this.onFeedbackChange.emit(updatedFeedback);
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
