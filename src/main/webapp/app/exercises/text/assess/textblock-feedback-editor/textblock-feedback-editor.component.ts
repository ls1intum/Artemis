import { AfterViewInit, Component, ElementRef, EventEmitter, HostBinding, Input, Output, ViewChild } from '@angular/core';
import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { FeedbackConflictType } from 'app/entities/feedback-conflict';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { lastValueFrom } from 'rxjs';

@Component({
    selector: 'jhi-textblock-feedback-editor',
    templateUrl: './textblock-feedback-editor.component.html',
    styleUrls: ['./textblock-feedback-editor.component.scss'],
})
export class TextblockFeedbackEditorComponent implements AfterViewInit {
    readonly FeedbackType = FeedbackType;

    @Input() textBlock: TextBlock = new TextBlock();
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
    listOfBlocksWithFeedback: any[];

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

    constructor(
        public structuredGradingCriterionService: StructuredGradingCriterionService,
        protected modalService: NgbModal,
        protected assessmentsService: TextAssessmentService,
    ) {}

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

    // this method fires the modal service and shows a modal after connecting feedback with its respective blocks
    async openOriginOfFeedbackModal(content: any) {
        await this.connectAutomaticFeedbackOriginBlocksWithFeedback();
        this.modalService.open(content, { size: 'lg' });
    }

    /**
     * This method is used to find the submission used for making the current Automatic Feedback and retrieve its blocks.
     * The blocks are then structured and set as a local property of this component to be shown in a modal
     */
    async connectAutomaticFeedbackOriginBlocksWithFeedback() {
        // retrieve participation and submission references for the Automatic Feedback generated
        const participationId = this.feedback.suggestedFeedbackParticipationReference ? this.feedback.suggestedFeedbackParticipationReference : -1;
        const submissionId = this.feedback.suggestedFeedbackOriginSubmissionReference ? this.feedback.suggestedFeedbackOriginSubmissionReference : -1;
        if (participationId >= 0 && submissionId >= 0) {
            // finds the corresponding submission where the automatic feedback came from
            const participation: StudentParticipation = await lastValueFrom(this.assessmentsService.getFeedbackDataForExerciseSubmission(participationId, submissionId));

            // connect the feedback with its respective block if any.
            let blocks: any[] = participation.submissions?.values().next().value.blocks;
            // Sort blocks to show them in order.
            blocks = blocks.sort((a, b) => a!.startIndex! - b!.startIndex!);
            const feedbacks: any[] = participation.submissions?.values().next().value.latestResult.feedbacks;

            // set list of blocks to be shown in the modal
            this.listOfBlocksWithFeedback = blocks
                .map((block) => {
                    const blockFeedback = feedbacks.find((feedback) => feedback.reference === block.id);
                    return {
                        text: block.text,
                        feedback: blockFeedback && blockFeedback.detailText,
                        credits: blockFeedback ? blockFeedback.credits : 0,
                        reusedCount: blockFeedback && block.numberOfAffectedSubmissions,
                        type: this.feedback.suggestedFeedbackReference === block.id ? 'AUTOMATIC' : 'MANUAL',
                    };
                })
                .filter((item) => item.text);
        }
    }
}
