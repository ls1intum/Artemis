import { Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { FeedbackConflict, FeedbackConflictType } from 'app/entities/feedback-conflict';

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
    @Input() submission: TextSubmission;
    @Input() textBlockRefs: TextBlockRef[];
    @Input() readOnly: boolean;
    @Input() selectedFeedbackIdWithConflicts?: number;
    @Input() conflictMode: boolean;
    @Input() isLeftConflictingFeedback: boolean;
    @Input() conflictingAssessments: FeedbackConflict[];
    @Output() textBlockRefsChange = new EventEmitter<TextBlockRef[]>();
    @Output() textBlockRefsAddedRemoved = new EventEmitter<void>();
    @Output() onConflictsClicked = new EventEmitter<number>();
    @Output() didSelectConflictingFeedback = new EventEmitter<number>();
    autoTextBlockAssessment = true;
    selectedRef?: TextBlockRef;
    wordCount = 0;
    characterCount = 0;

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

        this.textBlockRefs.sort((a, b) => a.block!.startIndex! - b.block!.startIndex!);
    }

    @HostListener('document:keydown.alt', ['$event', 'false'])
    @HostListener('document:keyup.alt', ['$event', 'true'])
    onAltToggle($event: KeyboardEvent, toggleValue: boolean) {
        this.autoTextBlockAssessment = toggleValue;
    }

    /**
     * Emit the reference change of text blocks
     */
    textBlockRefsChangeEmit(): void {
        this.textBlockRefsChange.emit(this.textBlockRefs);
    }

    addTextBlockRef(ref: TextBlockRef): void {
        this.textBlockRefs.push(ref);
        this.textBlockRefsAddedRemoved.emit();
    }

    removeTextBlockRef(ref: TextBlockRef): void {
        const index = this.textBlockRefs.findIndex((elem) => elem.block!.id! === ref.block!.id!);
        this.textBlockRefs.splice(index, 1);
        this.textBlockRefsAddedRemoved.emit();
    }

    getIsConflictingFeedback(ref: TextBlockRef): boolean {
        if (this.isLeftConflictingFeedback && this.selectedFeedbackIdWithConflicts) {
            return this.selectedFeedbackIdWithConflicts === ref.feedback?.id;
        }
        return this.conflictingAssessments?.some((textAssessmentConflict) => textAssessmentConflict.conflictingFeedbackId === ref.feedback?.id);
    }

    getConflictType(ref: TextBlockRef): FeedbackConflictType | undefined {
        const conflict = this.conflictingAssessments?.find((textAssessmentConflict) => textAssessmentConflict.conflictingFeedbackId === ref.feedback?.id);
        if (conflict) {
            return conflict.type!;
        }
        return undefined;
    }

    didSelectRef(ref: TextBlockRef): void {
        this.selectedRef = ref;
        if (this.conflictMode && !this.isLeftConflictingFeedback) {
            this.didSelectConflictingFeedback.emit(ref?.feedback?.id);
        }
    }
}
