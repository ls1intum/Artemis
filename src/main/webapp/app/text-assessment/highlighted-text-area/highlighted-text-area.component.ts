import { Component, DoCheck, Input, IterableDiffers, OnChanges, SimpleChanges } from '@angular/core';
import { Feedback } from 'app/entities/feedback';
import { HighlightColors } from 'app/text-assessment/highlight-colors';
import { TextBlock } from 'app/entities/text-block/text-block.model';

@Component({
    selector: 'jhi-highlighted-text-area',
    templateUrl: './highlighted-text-area.component.html',
    styles: [],
})
export class HighlightedTextAreaComponent implements OnChanges, DoCheck {
    @Input() public submissionText: string;
    @Input() public assessments: Feedback[];
    @Input() public blocks: (TextBlock | undefined)[];
    public displayedText: string;
    private differ: any;

    constructor(differs: IterableDiffers) {
        this.differ = differs.find([]).create(undefined);
    }

    get submissionTextWithHtmlLinebreaks(): string {
        if (!this.submissionText) {
            return '';
        }
        return this.submissionText.replace(/(?:\r\n|\r|\n)/g, '<br>');
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes) {
            this.displayedText = this.highlightText();
        }
    }

    ngDoCheck(): void {
        const changes = this.differ.diff(this.assessments);
        if (changes) {
            this.displayedText = this.highlightText();
        }
    }

    highlightText(): string {
        if (!this.assessments) {
            return this.submissionTextWithHtmlLinebreaks;
        }

        return this.assessments.reduce((content: string, assessment: Feedback, currentIndex: number) => {
            if (assessment.reference == null) {
                return content;
            }

            /**
             * For now, we want to support two kind of Feedback to Text references:
             * (1) The text is stored in the `feedback.reference` as is.
             * (2) feedback.reference contains a text block id.
             * Matching for ids is done in the `TextAssessmentComponent` and `this.blocks[currentIndex]` is only defined for case (2).
             */
            const replacementString: string = this.blocks && this.blocks[currentIndex] ? this.blocks[currentIndex]!.text : assessment.reference;

            return content.replace(replacementString, `<span class="highlight ${HighlightColors.forIndex(currentIndex)}">${replacementString}</span>`);
        }, this.submissionTextWithHtmlLinebreaks);
    }
}
