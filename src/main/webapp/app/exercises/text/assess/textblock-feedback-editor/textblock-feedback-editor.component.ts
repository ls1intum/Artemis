import { AfterViewInit, Component, ElementRef, EventEmitter, HostBinding, Input, Output, ViewChild } from '@angular/core';
import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { FeedbackConflictType } from 'app/entities/feedback-conflict';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';

@Component({
    selector: 'jhi-textblock-feedback-editor',
    templateUrl: './textblock-feedback-editor.component.html',
    styleUrls: ['./textblock-feedback-editor.component.scss'],
})
export class TextblockFeedbackEditorComponent implements AfterViewInit {
    readonly FeedbackType = FeedbackType;

    @Input() textBlock: TextBlock;
    @Input() feedback: Feedback = new Feedback();
    @Output() feedbackChange = new EventEmitter<Feedback>();
    @Output() close = new EventEmitter<void>();
    @Output() onFocus = new EventEmitter<void>();
    @Output() onConflictsClicked = new EventEmitter<number>();
    @ViewChild('detailText') textareaRef: ElementRef;
    @ViewChild(ConfirmIconComponent) confirmIconComponent: ConfirmIconComponent;
    @Input() disableEditScore = false;
    @Input() readOnly: boolean;
    @Input() isConflictingFeedback: boolean;
    @Input() conflictMode: boolean;
    @Input() conflictType?: FeedbackConflictType;
    @Input() isLeftConflictingFeedback: boolean;
    @Input() isSelectedConflict: boolean;
    @Input() highlightDifferences: boolean;
    private textareaElement: HTMLTextAreaElement;
    private static creditChanged = false;
    protected assessmentsService: TextAssessmentService;

    @HostBinding('class.alert') @HostBinding('class.alert-dismissible') readonly classes = true;

    @HostBinding('class.alert-secondary') get neutralFeedbackClass(): boolean {
        return !this.conflictMode ? this.feedback.credits === 0 : !this.isConflictingFeedback;
    }

    @HostBinding('class.alert-success') get positiveFeedbackClass(): boolean {
        return this.feedback.credits! > 0 && !this.conflictMode;
    }

    @HostBinding('class.alert-danger') get negativeFeedbackClass(): boolean {
        return this.feedback.credits! < 0 && !this.conflictMode;
    }

    @HostBinding('class.alert-warning') get conflictingFeedbackClass(): boolean {
        return this.isConflictingFeedback && this.conflictMode && !this.isSelectedConflict;
    }

    @HostBinding('class.alert-info') get selectedConflictingFeedbackClass(): boolean {
        return this.isSelectedConflict;
    }

    constructor(public structuredGradingCriterionService: StructuredGradingCriterionService) {}

    /**
     * Life cycle hook to indicate component initialization is done
     */
    ngAfterViewInit(): void {
        this.textareaElement = this.textareaRef.nativeElement as HTMLTextAreaElement;
        setTimeout(() => this.textareaAutogrow());
        if (this.feedback.gradingInstruction && this.feedback.gradingInstruction.usageCount !== 0) {
            this.disableEditScore = true;
        } else {
            this.disableEditScore = false;
        }
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

    get isConflictingFeedbackEditable(): boolean {
        return this.conflictMode && this.isLeftConflictingFeedback && this.isConflictingFeedback;
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
        this.close.emit();
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
        if (this.conflictMode && !this.isLeftConflictingFeedback && this.isConflictingFeedback) {
            return;
        }
        this.textareaElement.focus();
    }

    /**
     * Hook to indicate a score click
     */
    onScoreClick(event: MouseEvent): void {
        event.preventDefault();
        console.warn('Clicked on Score-->', this.feedback.credits);
    }

    /**
     * Hook to indicate changes in the feedback editor
     */
    didChange(): void {
        Feedback.updateFeedbackTypeOnChange(this.feedback);
        this.feedbackChange.emit(this.feedback);
        if (this.feedback.credits !== 0 && !TextblockFeedbackEditorComponent.creditChanged) {
            console.warn('Call backend and receive number affected', this.feedback, this.feedback.credits);
            TextblockFeedbackEditorComponent.creditChanged = true;
            // this.assessmentsService
        } else if (this.feedback.credits === 0) {
            console.warn('Reset value');
            TextblockFeedbackEditorComponent.creditChanged = false;
        }
    }

    connectFeedbackWithInstruction(event: Event) {
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.feedback, event);
        if (this.feedback.gradingInstruction && this.feedback.gradingInstruction.usageCount !== 0) {
            this.disableEditScore = true;
        } else {
            this.disableEditScore = false;
        }
        this.didChange();
    }
}
