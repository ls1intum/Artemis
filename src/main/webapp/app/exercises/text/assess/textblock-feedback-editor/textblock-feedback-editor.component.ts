import { AfterViewInit, Component, ElementRef, EventEmitter, HostBinding, Input, Output, ViewChild } from '@angular/core';
import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { FeedbackConflictType } from 'app/entities/feedback-conflict';

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
    private textareaElement: HTMLTextAreaElement;

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
    }

    /**
     * Hook to indicate changes in the feedback editor
     */
    didChange(): void {
        Feedback.updateFeedbackTypeOnChange(this.feedback);
        this.feedbackChange.emit(this.feedback);
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
