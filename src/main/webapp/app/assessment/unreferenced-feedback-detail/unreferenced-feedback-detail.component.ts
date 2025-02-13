import { Component, EventEmitter, Input, InputSignal, OnInit, Output, inject, input } from '@angular/core';
import { faCheck, faExclamation, faExclamationTriangle, faQuestionCircle, faTrash, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { Subject } from 'rxjs';
import { FeedbackService } from 'app/exercises/shared/feedback/feedback.service';
import { FeedbackSuggestionBadgeComponent } from '../../exercises/shared/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AssessmentCorrectionRoundBadgeComponent } from './assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeedbackContentPipe } from 'app/shared/pipes/feedback-content.pipe';
import { QuotePipe } from 'app/shared/pipes/quote.pipe';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    templateUrl: './unreferenced-feedback-detail.component.html',
    styleUrls: ['./unreferenced-feedback-detail.component.scss'],
    imports: [
        FeedbackSuggestionBadgeComponent,
        GradingInstructionLinkIconComponent,
        DeleteButtonDirective,
        FaIconComponent,
        TranslateDirective,
        FormsModule,
        NgbTooltip,
        FaLayersComponent,
        AssessmentCorrectionRoundBadgeComponent,
        ArtemisTranslatePipe,
        FeedbackContentPipe,
        QuotePipe,
    ],
})
export class UnreferencedFeedbackDetailComponent implements OnInit {
    structuredGradingCriterionService = inject(StructuredGradingCriterionService);

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

    ngOnInit() {
        this.loadLongFeedback();
    }

    /**
     * Call this method to load long feedback if needed
     */
    public async loadLongFeedback() {
        if (this.feedback.id && this.feedback.hasLongFeedbackText) {
            this.feedback.detailText = await this.feedbackService.getLongFeedbackText(this.feedback.id);
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
        event.stopPropagation();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.feedback, event);
        this.onFeedbackChange.emit(this.feedback);
    }
}
