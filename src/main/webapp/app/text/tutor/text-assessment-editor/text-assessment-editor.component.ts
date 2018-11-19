import {
    Component,
    DoCheck,
    EventEmitter,
    Input,
    IterableDifferFactory,
    IterableDiffers,
    OnChanges,
    Output,
    SimpleChanges
} from '@angular/core';
import { SelectionRectangle, TextSelectEvent } from 'app/text/tutor/text-assessment-editor/text-select.directive';
import { Color, colorForIndex, colors } from 'app/text/tutor';
import { TextAssessment } from 'app/entities/text-assessments/text-assessments.model';

@Component({
    selector: 'jhi-text-assessment-editor',
    templateUrl: './text-assessment-editor.component.html',
    styleUrls: ['./text-assessment-editor.component.scss']
})
export class TextAssessmentEditorComponent implements OnChanges, DoCheck {
    public hostRectangle: SelectionRectangle;
    @Input() public submissionText: string;
    @Input() public assessments: TextAssessment[];
    @Output() public assessedText = new EventEmitter<string>();
    public displayedText: string;
    private selectedText: string;
    public readonly colors = colors;
    private differ: any;

    constructor(differs: IterableDiffers) {
        this.differ = differs.find([]).create(null);
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
        return this.assessments.reduce(
            (content: string, assessment: TextAssessment, currentIndex: number) =>
                content.replace(
                    new RegExp(assessment.text, 'gi'),
                    match => `<span class="highlight ${colorForIndex(currentIndex)}">${match}</span>`
                ),
            this.submissionText
        );
    }
}
