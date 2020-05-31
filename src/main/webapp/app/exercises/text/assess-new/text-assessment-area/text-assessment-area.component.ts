import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges } from '@angular/core';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextBlock } from 'app/entities/text-block.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';

@Component({
    selector: 'jhi-text-assessment-area',
    template: `
        <div>
            <span class="badge badge-primary mb-2">
                {{ 'artemisApp.textExercise.wordCount' | translate: { count: wordCount } }}
            </span>
            <span class="badge badge-primary mb-2">
                {{ 'artemisApp.textExercise.characterCount' | translate: { count: characterCount } }}
            </span>
        </div>
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
    wordCount = 0;
    characterCount = 0;

    constructor(private stringCountService: StringCountService) {}

    /**
     * Life cycle hook to indicate component change
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.submission) {
            const { text } = changes.submission.currentValue as TextSubmission;
            this.wordCount = this.stringCountService.countWords(text);
            this.characterCount = this.stringCountService.countCharacters(text);
        }

        this.textBlockRefs.sort((a, b) => a.block.startIndex - b.block.startIndex);
    }

    /**
     * Emit the reference change of text blocks
     */
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
