import { Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';

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
    @Output() textBlockRefsChange = new EventEmitter<TextBlockRef[]>();
    @Output() textBlockRefsAddedRemoved = new EventEmitter<void>();
    autoTextBlockAssessment = true;
    selectedRef?: TextBlockRef;
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
}
