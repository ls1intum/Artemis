import { Component, DoCheck, EventEmitter, Input, IterableDiffers, OnChanges, Output, SimpleChanges } from '@angular/core';
import { SelectionRectangle, TextSelectEvent } from './text-select.directive';
import { HighlightColors } from '../highlight-colors';
import { Feedback } from 'app/entities/feedback';

@Component({
    selector: 'jhi-text-assessment-editor',
    templateUrl: './text-assessment-editor.component.html',
    styleUrls: ['./text-assessment-editor.component.scss']
})
export class TextAssessmentEditorComponent implements OnChanges, DoCheck {
    public hostRectangle: SelectionRectangle;
    @Input() public submissionText: string;
    @Input() public assessments: Feedback[];
    @Output() public assessedText = new EventEmitter<string>();
    public displayedText: string;
    private selectedText: string;
    private differ: any;

    constructor(differs: IterableDiffers) {
        this.differ = differs.find([]).create(null);
    }

    get submissionTextWithHtmlLinebreaks(): string {
        if (!this.submissionText) { return ''; }
        return this.submissionText.replace('\n', '<br />');
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

    didSelectSolutionText(event: TextSelectEvent): void {
        // If a new selection has been created, the viewport and host rectangles will
        // exist. Or, if a selection is being removed, the rectangles will be null.
        if (event.hostRectangle) {
            this.hostRectangle = event.hostRectangle;
            this.selectedText = event.text;
        } else {
            this.hostRectangle = null;
            this.selectedText = null;
        }
    }

    deselectText(): void {
        document.getSelection().removeAllRanges();
        this.hostRectangle = null;
        this.selectedText = null;
    }

    assessSelection(): void {
        if (this.selectedText.trim().length === 0) {
            return;
        }

        this.assessedText.emit(this.selectedText.trim());
        this.deselectText();
    }

    highlightText(): string {
        if (!this.assessments) { return this.submissionTextWithHtmlLinebreaks; }

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
