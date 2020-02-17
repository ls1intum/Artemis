import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HighlightColors } from 'app/text-assessment/highlight-colors';
import { TextBlock } from 'app/entities/text-block/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback/feedback.model';

@Component({
    selector: 'jhi-assessment-detail',
    templateUrl: './assessment-detail.component.html',
    styleUrls: ['./assessment-detail.component.scss'],
})
export class AssessmentDetailComponent {
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
        const confirmationMessage = referencedText ? `Delete Assessment for ${referencedText}?` : 'Delete Assessment?';
        const confirmation = confirm(confirmationMessage);
        if (confirmation) {
            this.deleteAssessment.emit(this.assessment);
        }
    }
}
