import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HighlightColors } from '../highlight-colors';
import { Feedback, FeedbackType } from 'app/entities/feedback';
import { TextBlock } from 'app/entities/text-block/text-block.model';

@Component({
    selector: 'jhi-text-assessment-detail',
    templateUrl: './text-assessment-detail.component.html',
    styles: [],
})
export class TextAssessmentDetailComponent {
    @Input() public assessment: Feedback;
    @Input() public block: TextBlock | undefined;
    @Output() public assessmentChange = new EventEmitter<Feedback>();
    @Input() public highlightColor: HighlightColors.Color;
    @Output() public deleteAssessment = new EventEmitter<Feedback>();
    @Input() public disabled = false;

    public FeedbackType_AUTOMATIC = FeedbackType.AUTOMATIC;

    public emitChanges(): void {
        if (this.assessment.type === FeedbackType.AUTOMATIC) {
            this.assessment.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
        this.assessmentChange.emit(this.assessment);
    }

    public delete() {
        const referencedText = this.block ? this.block.text : this.assessment.reference;
        const confirmation = confirm(`Delete Assessment "${referencedText}"?`);
        if (confirmation) {
            this.deleteAssessment.emit(this.assessment);
        }
    }
}
