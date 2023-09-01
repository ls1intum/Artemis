import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faCheck, faExclamation, faExclamationTriangle, faQuestionCircle, faTrash, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, Feedback, FeedbackType } from 'app/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    templateUrl: './unreferenced-feedback-detail.component.html',
    styleUrls: ['./unreferenced-feedback-detail.component.scss'],
})
export class UnreferencedFeedbackDetailComponent {
    @Input() public feedback: Feedback;
    @Input() isSuggestion: boolean;
    @Input() public readOnly: boolean;
    @Input() highlightDifferences: boolean;

    @Output() public onFeedbackChange = new EventEmitter<Feedback>();
    @Output() public onFeedbackDelete = new EventEmitter<Feedback>();
    @Output() onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output() onDiscardSuggestion = new EventEmitter<Feedback>();

    // Icons
    faTrashAlt = faTrashAlt;
    faQuestionCircle = faQuestionCircle;
    faExclamation = faExclamation;
    faExclamationTriangle = faExclamationTriangle;
    faCheck = faCheck;
    faTrash = faTrash;

    // Expose to template
    protected readonly Feedback = Feedback;

    constructor(
        private translateService: TranslateService,
        public structuredGradingCriterionService: StructuredGradingCriterionService,
    ) {}

    /**
     * Emits assessment changes to parent component
     */
    public emitChanges(): void {
        if (this.feedback.type === FeedbackType.AUTOMATIC) {
            this.feedback.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
        if (Feedback.isFeedbackSuggestion(this.feedback)) {
            // Change feedback suggestion type to adapted
            this.feedback.text = (this.feedback.text ?? FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER).replace(
                FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
                FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
            );
        }
        this.onFeedbackChange.emit(this.feedback);
    }
    /**
     * Emits the deletion of an assessment
     */
    public delete() {
        const text: string = this.translateService.instant('artemisApp.feedback.delete.question', { id: this.feedback.id ?? '' });
        const confirmation = confirm(text);
        if (confirmation) {
            this.onFeedbackDelete.emit(this.feedback);
        }
    }

    updateFeedbackOnDrop(event: Event) {
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.feedback, event);
        this.onFeedbackChange.emit(this.feedback);
    }
}
