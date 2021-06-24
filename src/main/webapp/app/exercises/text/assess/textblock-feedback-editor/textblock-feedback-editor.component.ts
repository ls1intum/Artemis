import { AfterViewInit, Component, ElementRef, EventEmitter, HostBinding, Input, Output, ViewChild } from '@angular/core';
import { TextBlock } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { FeedbackConflictType } from 'app/entities/feedback-conflict';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Observable, lastValueFrom } from 'rxjs';
import { text } from '@fortawesome/fontawesome-svg-core';

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

    async openConfirmationModal(content: any) {
        console.warn('first', this.feedback.suggestedFeedbackParticipationReference);
        const participationId = this.feedback.suggestedFeedbackParticipationReference ? this.feedback.suggestedFeedbackParticipationReference : -1;

        const submissionId = this.feedback.suggestedFeedbackOriginSubmissionReference ? this.feedback.suggestedFeedbackOriginSubmissionReference : -1;
        if (participationId >= 0 && submissionId >= 0) {
            console.warn('second', this.feedback.suggestedFeedbackOriginSubmissionReference);

            const participation: StudentParticipation = await lastValueFrom(this.assessmentsService.getFeedbackDataForExerciseSubmission(participationId, submissionId));

            console.log(participation.submissions?.values().next().value.blocks);

            const blocks: any[] = participation.submissions?.values().next().value.blocks;
            const feedbacks: any[] = participation.submissions?.values().next().value.latestResult.feedbacks;

            console.warn({ feedbacks });
            console.warn('THIS.FEEDBACK', this.feedback);
            let c = [];
            c.push({
                text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum.',
                feedback: undefined,
                credits: 0,
                type: 'MANUAL',
                reusedCount: 0,
            });
            let test = c.concat(
                blocks
                    .map((block) => {
                        const blockFeedback = feedbacks.find((feedback) => feedback.reference === block.id);
                        console.warn('map-block-reference', block.reference);
                        return {
                            text: block.text,
                            feedback: blockFeedback && blockFeedback.detailText,
                            credits: blockFeedback ? blockFeedback.credits : 0,
                            reusedCount: blockFeedback && block.numberOfAffectedSubmissions,
                            type: this.feedback.suggestedFeedbackReference === block.id ? 'AUTOMATIC' : 'MANUAL',
                        };
                    })
                    .filter((item) => item != undefined),
            );

            test.push({
                text: 'another sentence.',
                feedback: undefined,
                credits: 0,
                type: 'MANUAL',
                reusedCount: 0,
            });
            test.push({
                text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim venia.',
                feedback: undefined,
                credits: 0,
                type: 'MANUAL',
                reusedCount: 0,
            });
            test.push({
                text: 'yet another sentence.',
                feedback: undefined,
                credits: 0,
                type: 'MANUAL',
                reusedCount: 1,
            });
            test.push({
                text: 'That is why I think it is better to use it instead.',
                feedback: undefined,
                credits: 1,
                type: 'MANUAL',
                reusedCount: 0,
            });
            test.push({
                text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.',
                feedback: undefined,
                credits: 0,
                type: 'MANUAL',
                reusedCount: 0,
            });

            this.listOfBlocksWithFeedback = test;

            this.modalService.open(content, { size: 'lg' }).result.then(
                (result: string) => {
                    if (result === 'confirm') {
                        console.warn('Confirm?');
                        console.warn(participation);
                    }
                    console.warn('test', participation);
                },
                () => {},
            );
        }
    }
}
