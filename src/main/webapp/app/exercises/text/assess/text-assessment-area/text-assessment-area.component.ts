import { Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { FeedbackConflict, FeedbackConflictType } from 'app/entities/feedback-conflict';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';

@Component({
    selector: 'jhi-text-assessment-area',
    templateUrl: './text-assessment-area.component.html',
    styles: [
        `
            :host {
                width: 100%;
            }
        `,
    ],
})
export class TextAssessmentAreaComponent implements OnChanges {
    // inputs
    @Input() submission: TextSubmission;
    @Input() textBlockRefs: TextBlockRef[];
    @Input() readOnly: boolean;
    @Input() selectedFeedbackIdWithConflicts?: number;
    @Input() conflictMode: boolean;
    @Input() isLeftConflictingFeedback: boolean;
    @Input() feedbackConflicts: FeedbackConflict[];
    @Input() highlightDifferences: boolean;
    @Input() criteria?: GradingCriterion[];
    @Input() allowManualBlockSelection = true;

    // outputs
    @Output() textBlockRefsChange = new EventEmitter<TextBlockRef[]>();
    @Output() textBlockRefsAddedRemoved = new EventEmitter<void>();
    @Output() onConflictsClicked = new EventEmitter<number>();
    @Output() didSelectConflictingFeedback = new EventEmitter<number>();
    autoTextBlockAssessment = true;
    selectedRef?: TextBlockRef;
    wordCount = 0;
    characterCount = 0;
    isConflictingFeedbackMap?: Map<TextBlockRef, boolean>;
    conflictTypeMap?: Map<TextBlockRef, FeedbackConflictType | undefined>;

    constructor(private stringCountService: StringCountService) {
        this.isLeftConflictingFeedback = false;
    }

    /**
     * Life cycle hook to indicate component change
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.submission && changes.submission.currentValue) {
            const { text } = changes.submission.currentValue as TextSubmission;
            this.wordCount = this.stringCountService.countWords(text);
            this.characterCount = this.stringCountService.countCharacters(text);
        }

        if (changes.textBlockRefs && changes.textBlockRefs.currentValue) {
            this.isConflictingFeedbackMap = new Map(this.textBlockRefs.map((ref) => [ref, this.getIsConflictingFeedback(ref)]));
            this.conflictTypeMap = new Map(this.textBlockRefs.map((ref) => [ref, this.getConflictType(ref)]));
        }

        this.textBlockRefs.sort((a, b) => a.block!.startIndex! - b.block!.startIndex!);
    }

    @HostListener('document:keydown.alt', ['$event', 'false'])
    @HostListener('document:keyup.alt', ['$event', 'true'])
    onAltToggle(event: KeyboardEvent, toggleValue: boolean) {
        if (!this.allowManualBlockSelection) {
            return;
        }
        this.autoTextBlockAssessment = toggleValue;
    }

    /**
     * Emit the reference change of text blocks
     */
    textBlockRefsChangeEmit(): void {
        this.textBlockRefsChange.emit(this.textBlockRefs);
    }

    /**
     * It is called if a text block is added manually.
     * @param ref - added text block
     */
    addTextBlockRef(ref: TextBlockRef): void {
        this.textBlockRefs.push(ref);
        this.textBlockRefsAddedRemoved.emit();
    }

    /**
     * It is called if the assessment for a text block is deleted. So, textBlockRef is deleted
     * @param ref - TextBlockRef that has a deleted assessment(feedback).
     */
    removeTextBlockRef(ref: TextBlockRef): void {
        const index = this.textBlockRefs.findIndex((elem) => elem.block!.id! === ref.block!.id!);
        this.textBlockRefs.splice(index, 1);
        this.textBlockRefsAddedRemoved.emit();
    }

    /**
     * Checks if the passed TextBlockRef has a conflicting feedback.
     * If this is the left assessment checks the left conflicting feedback id otherwise searches the feedback id inside the conflicts array.
     * Returns false for text-submission-assessment component
     * @param ref - TextBlockRef to check if its feedback is conflicting.
     */
    private getIsConflictingFeedback(ref: TextBlockRef): boolean {
        if (this.isLeftConflictingFeedback && this.selectedFeedbackIdWithConflicts) {
            return this.selectedFeedbackIdWithConflicts === ref.feedback?.id;
        }
        return this.feedbackConflicts?.some((feedbackConflict) => feedbackConflict.conflictingFeedbackId === ref.feedback?.id);
    }

    /**
     * Gets FeedbackConflictType from conflicting feedback. Returns undefined for non conflicts.
     * @param ref - TextBlockRef to get FeedbackConflictType of its conflicting feedback.
     */
    private getConflictType(ref: TextBlockRef): FeedbackConflictType | undefined {
        return this.feedbackConflicts?.find((feedbackConflict) => feedbackConflict.conflictingFeedbackId === ref.feedback?.id)?.type;
    }

    /**
     * It is called when a text block is selected.
     * If it is in conflict mode and right assessment area, emit feedback id since it is a selected conflict.
     * @param ref - selected TextBlockRef
     */
    didSelectRef(ref?: TextBlockRef): void {
        this.selectedRef = ref;
        if (this.conflictMode && !this.isLeftConflictingFeedback) {
            this.didSelectConflictingFeedback.emit(ref?.feedback?.id);
        }
    }
}
