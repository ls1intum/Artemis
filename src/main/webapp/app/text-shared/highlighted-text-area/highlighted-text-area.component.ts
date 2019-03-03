import { Component, DoCheck, Input, IterableDiffers, OnChanges, SimpleChanges } from '@angular/core';
import { Feedback } from 'app/entities/feedback';
import { HighlightColors } from 'app/text-shared/highlight-colors';

@Component({
    selector: 'jhi-highlighted-text-area',
    templateUrl: './highlighted-text-area.component.html',
    styles: []
})
export class HighlightedTextAreaComponent implements OnChanges, DoCheck {
    @Input() public submissionText: string;
    @Input() public assessments: Feedback[];
    public displayedText: string;
    private differ: any;

    constructor(differs: IterableDiffers) {
        this.differ = differs.find([]).create(null);
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

        return this.assessments.reduce(
            (content: string, assessment: Feedback, currentIndex: number) =>
                content.replace(
                    assessment.reference,
                    `<span class="highlight ${HighlightColors.forIndex(currentIndex)}">${assessment.reference}</span>`
                ),
            this.submissionTextWithHtmlLinebreaks
        );
    }
}
