import { Component, EventEmitter, Input, Output, OnChanges } from '@angular/core';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextBlock } from 'app/entities/text-block.model';

@Component({
    selector: 'jhi-text-assessment-area',
    template: `
        <jhi-textblock-assessment-card
            *ngFor="let ref of textBlockRefs"
            [textBlockRef]="ref"
            [selected]="selectedRef === ref"
            (didSelect)="selectedRef = $event"
            (didChange)="textBlockRefsChangeEmit()"
        ></jhi-textblock-assessment-card>
    `,
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
    @Output() textBlockRefsChange = new EventEmitter<TextBlockRef[]>();
    selectedRef: TextBlockRef | null = null;

    ngOnChanges(): void {
        this.textBlockRefs.sort((a, b) => a.block.startIndex - b.block.startIndex);
    }

    textBlockRefsChangeEmit(): void {
        this.textBlockRefsChange.emit(this.textBlockRefs);
    }

    /**
     * Delete Text Block at index and merge with adjacent blocks without feedback.
     * @param index of Text Block to be deleted.
     *
     * Note: This function is currently unused, but will be used in future versions.
     */
    deleteAtIndex(index: number): void {
        const ref = this.textBlockRefs[index];
        const prev = index > 0 ? this.textBlockRefs[index - 1] : undefined;
        const next = index + 1 < this.textBlockRefs.length ? this.textBlockRefs[index + 1] : undefined;

        const newBlock = new TextBlock();
        newBlock.startIndex = prev && !prev.feedback ? prev.block.startIndex : ref.block.startIndex;
        newBlock.endIndex = next && !next.feedback ? next.block.endIndex : ref.block.endIndex;
        newBlock.submission = this.submission;
        newBlock.setTextFromSubmission();
        ref.block = newBlock;
        ref.feedback = undefined;

        if (next && !next.feedback) {
            this.textBlockRefs.splice(index + 1, 1);
        }
        if (prev && !prev.feedback) {
            this.textBlockRefs.splice(index - 1, 1);
        }

        this.textBlockRefsChangeEmit();
    }
}
