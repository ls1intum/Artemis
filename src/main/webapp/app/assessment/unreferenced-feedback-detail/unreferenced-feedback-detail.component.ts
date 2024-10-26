import { Component, EventEmitter, Input, InputSignal, OnInit, Output, inject, input } from '@angular/core';
import { faCheck, faExclamation, faExclamationTriangle, faQuestionCircle, faTrash, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { Subject } from 'rxjs';
import { FeedbackService } from 'app/exercises/shared/feedback/feedback.service';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    templateUrl: './unreferenced-feedback-detail.component.html',
    styleUrls: ['./unreferenced-feedback-detail.component.scss'],
})
export class UnreferencedFeedbackDetailComponent implements OnInit {
    @Input() public feedback: Feedback;
    resultId: InputSignal<number> = input.required<number>();
    @Input() isSuggestion: boolean;
    @Input() public readOnly: boolean;
    @Input() highlightDifferences: boolean;
    @Input() useDefaultFeedbackSuggestionBadgeText: boolean;

    @Output() public onFeedbackChange = new EventEmitter<Feedback>();
    @Output() public onFeedbackDelete = new EventEmitter<Feedback>();
    @Output() onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output() onDiscardSuggestion = new EventEmitter<Feedback>();
    private feedbackService = inject(FeedbackService);

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

    constructor(public structuredGradingCriterionService: StructuredGradingCriterionService) {}

    ngOnInit() {
        this.loadLongFeedback();
    }

    /**
     * Call this method to load long feedback if needed
     */
    public async loadLongFeedback() {
        if (this.feedback.hasLongFeedbackText) {
            this.feedback.detailText = await this.feedbackService.getLongFeedbackText(this.resultId, this.feedback.id!);
            this.onFeedbackChange.emit(this.feedback);
        }
    }

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
