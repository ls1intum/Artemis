import { AfterViewInit, Component, ElementRef, EventEmitter, HostBinding, Input, Output, ViewChild, inject } from '@angular/core';
import { TextBlock } from 'app/entities/text/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { FeedbackSuggestionBadgeComponent } from 'app/exercises/shared/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentEventType } from 'app/entities/text/text-assesment-event.model';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { faAngleRight, faEdit, faExclamationTriangle, faQuestionCircle, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { GradingInstructionLinkIconComponent } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { TextblockFeedbackDropdownComponent } from './dropdown/textblock-feedback-dropdown.component';
import { FormsModule } from '@angular/forms';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
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

    @Input() textBlock: TextBlock = new TextBlock();
    @Input() feedback: Feedback = new Feedback();
    @Output() feedbackChange = new EventEmitter<Feedback>();
    @Output() onClose = new EventEmitter<void>();
    @Output() onFocus = new EventEmitter<void>();
    @ViewChild('detailText') textareaRef: ElementRef;
    @ViewChild(ConfirmIconComponent) confirmIconComponent: ConfirmIconComponent;
    @Input() readOnly: boolean;
    @Input() highlightDifferences: boolean;
    @Input() criteria?: GradingCriterion[];
    private textareaElement: HTMLTextAreaElement;

    // Expose to template
    protected readonly Feedback = Feedback;

    @HostBinding('class.alert') @HostBinding('class.alert-dismissible') readonly classes = true;

    @HostBinding('class.alert-secondary') get neutralFeedbackClass(): boolean {
        return this.feedback.credits === 0;
    }

    @HostBinding('class.alert-success') get positiveFeedbackClass(): boolean {
        return this.feedback.credits! > 0;
    }

    @HostBinding('class.alert-danger') get negativeFeedbackClass(): boolean {
        return this.feedback.credits! < 0;
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
        this.textareaElement = this.textareaRef.nativeElement as HTMLTextAreaElement;
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
        return this.feedback.credits === 0 && (this.feedback.detailText || '').length === 0;
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
        this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.DELETE_FEEDBACK, this.feedback.type, this.textBlock.type);
    }

    /**
     * Hook to indicate pressed Escape key
     */
    escKeyup(): void {
        if (this.canDismiss) {
            this.dismiss();
        } else {
            this.confirmIconComponent.toggle();
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
        this.feedback.correctionStatus = undefined;
    }

    /**
     * Hook to indicate changes in the feedback editor
     */
    didChange(): void {
        const feedbackTextBefore = this.feedback.text;
        Feedback.updateFeedbackTypeOnChange(this.feedback);
        this.feedbackChange.emit(this.feedback);
        // send event to analytics if the feedback was adapted (=> title text changes to have prefix with "adapted" in it)
        if (feedbackTextBefore !== this.feedback.text) {
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, this.feedback.type, this.textBlock.type);
        }
    }

    connectFeedbackWithInstruction(event: Event) {
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.feedback, event);

        // Reset the feedback correction status upon setting grading instruction in order to hide it.
        this.feedback.correctionStatus = undefined;

        this.didChange();
    }
}
