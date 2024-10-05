import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { faCheck, faExclamation, faExclamationTriangle, faQuestionCircle, faTrash, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    templateUrl: './unreferenced-feedback-detail.component.html',
    styleUrls: ['./unreferenced-feedback-detail.component.scss'],
})
export class UnreferencedFeedbackDetailComponent {
    structuredGradingCriterionService = inject(StructuredGradingCriterionService);

    @Input() public feedback: Feedback;
    @Input() isSuggestion: boolean;
    @Input() public readOnly: boolean;
    @Input() highlightDifferences: boolean;
    @Input() useDefaultFeedbackSuggestionBadgeText: boolean;

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
    readonly ButtonSize = ButtonSize;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    /**
     * Emits assessment changes to parent component
     */
    public emitChanges(): void {
        if (this.feedback.type === FeedbackType.AUTOMATIC) {
            this.feedback.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
        Feedback.updateFeedbackTypeOnChange(this.feedback);
        this.onFeedbackChange.emit(this.feedback);
    }

    /**
     * Emits the deletion of a feedback
     */
    public delete() {
        this.onFeedbackDelete.emit(this.feedback);
        this.dialogErrorSource.next('');
    }

    updateFeedbackOnDrop(event: Event) {
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.feedback, event);
        this.onFeedbackChange.emit(this.feedback);
    }
}
