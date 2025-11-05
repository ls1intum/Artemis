import { Component, OnInit, inject, input, model, output } from '@angular/core';
import { faCheck, faExclamation, faExclamationTriangle, faQuestionCircle, faTrash, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { Subject } from 'rxjs';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AssessmentCorrectionRoundBadgeComponent } from './assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeedbackContentPipe } from 'app/shared/pipes/feedback-content.pipe';
import { QuotePipe } from 'app/shared/pipes/quote.pipe';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';

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
        this.dialogErrorSource.next('');
    }

    updateFeedbackOnDrop(event: Event) {
        event.stopPropagation();
        const feedback = this.feedback();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(feedback, event);
        this.feedback.set(feedback);
        this.onFeedbackChange.emit(feedback);
    }
}
