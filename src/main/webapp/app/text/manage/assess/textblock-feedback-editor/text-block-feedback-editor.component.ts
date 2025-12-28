import { AfterViewInit, Component, ElementRef, HostBinding, inject, input, output, viewChild } from '@angular/core';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { faAngleRight, faEdit, faExclamationTriangle, faQuestionCircle, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { TextblockFeedbackDropdownComponent } from './dropdown/textblock-feedback-dropdown.component';
import { FormsModule } from '@angular/forms';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/manage/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-text-block-feedback-editor',
    templateUrl: './text-block-feedback-editor.component.html',
    styleUrls: ['./text-block-feedback-editor.component.scss'],
    imports: [
        FeedbackSuggestionBadgeComponent,
        FaIconComponent,
        NgbTooltip,
        ConfirmIconComponent,
        TranslateDirective,
        GradingInstructionLinkIconComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        TextblockFeedbackDropdownComponent,
        FormsModule,
        AssessmentCorrectionRoundBadgeComponent,
        ArtemisTranslatePipe,
    ],
})
export class TextBlockFeedbackEditorComponent implements AfterViewInit {
    private route = inject(ActivatedRoute);
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private textAssessmentAnalytics = inject(TextAssessmentAnalytics);

    readonly FeedbackType = FeedbackType;

    textBlock = input<TextBlock>(new TextBlock());
    feedback = input<Feedback>(new Feedback());
    feedbackChange = output<Feedback>();
    onClose = output<void>();
    onFocus = output<void>();
    textareaRef = viewChild.required<ElementRef>('detailText');
    confirmIconComponent = viewChild.required(ConfirmIconComponent);
    readOnly = input<boolean>();
    highlightDifferences = input<boolean>();
    criteria = input<GradingCriterion[]>();
    private textareaElement: HTMLTextAreaElement;

    // Expose to template
    protected readonly Feedback = Feedback;

    @HostBinding('class.alert') @HostBinding('class.alert-dismissible') readonly classes = true;

    @HostBinding('class.alert-secondary') get neutralFeedbackClass(): boolean {
        return this.feedback().credits === 0;
    }

    @HostBinding('class.alert-success') get positiveFeedbackClass(): boolean {
        return this.feedback().credits! > 0;
    }

    @HostBinding('class.alert-danger') get negativeFeedbackClass(): boolean {
        return this.feedback().credits! < 0;
    }

    // Icons
    faEdit = faEdit;
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faTimes = faTimes;
    faTrash = faTrash;
    faAngleRight = faAngleRight;

    constructor() {
        this.textAssessmentAnalytics.setComponentRoute(this.route);
    }

    /**
     * Life cycle hook to indicate component initialization is done
     */
    ngAfterViewInit(): void {
        this.textareaElement = this.textareaRef().nativeElement as HTMLTextAreaElement;
        setTimeout(() => this.textareaAutogrow());
    }

    /**
     * Increase size of text area automatically
     */
    textareaAutogrow(): void {
        this.textareaElement.style.height = '0px';
        this.textareaElement.style.height = `${this.textareaElement.scrollHeight}px`;
    }

    get canDismiss(): boolean {
        const feedbackValue = this.feedback();
        return feedbackValue.credits === 0 && (feedbackValue.detailText || '').length === 0;
    }

    /**
     * Set focus to feedback editor
     */
    inFocus(): void {
        this.onFocus.emit();
    }

    /**
     * Dismiss changes in feedback editor
     */
    dismiss(): void {
        this.onClose.emit();
        this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.DELETE_FEEDBACK, this.feedback().type, this.textBlock().type);
    }

    /**
     * Hook to indicate pressed Escape key
     */
    escKeyup(): void {
        if (this.canDismiss) {
            this.dismiss();
        } else {
            this.confirmIconComponent().toggle();
        }
    }

    /**
     * Set focus to the text area
     */
    focus(): void {
        this.textareaElement.focus();
    }

    /**
     * Hook to indicate a score click
     */
    onScoreClick(event: MouseEvent): void {
        event.preventDefault();

        // Reset the feedback correction status upon score change in order to hide it.
        this.feedback().correctionStatus = undefined;
    }

    /**
     * Hook to indicate changes in the feedback editor
     */
    didChange(): void {
        const feedbackValue = this.feedback();
        const feedbackTextBefore = feedbackValue.text;
        Feedback.updateFeedbackTypeOnChange(feedbackValue);
        this.feedbackChange.emit(feedbackValue);
        // send event to analytics if the feedback was adapted (=> title text changes to have prefix with "adapted" in it)
        if (feedbackTextBefore !== feedbackValue.text) {
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, feedbackValue.type, this.textBlock().type);
        }
    }

    connectFeedbackWithInstruction(event: Event) {
        const feedbackValue = this.feedback();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(feedbackValue, event);

        // Reset the feedback correction status upon setting grading instruction in order to hide it.
        feedbackValue.correctionStatus = undefined;

        this.didChange();
    }
}
