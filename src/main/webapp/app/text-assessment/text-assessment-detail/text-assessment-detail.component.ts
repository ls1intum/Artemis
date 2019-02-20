import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HighlightColors } from '../../text-shared/highlight-colors';
import { Feedback } from 'app/entities/feedback';

@Component({
    selector: 'jhi-text-assessment-detail',
    templateUrl: './text-assessment-detail.component.html',
    styles: []
})
export class TextAssessmentDetailComponent {
    @Input() public assessment: Feedback;
    @Output() public assessmentChange = new EventEmitter<Feedback>();
    @Input() public highlightColor: HighlightColors.Color;
    @Output() public deleteAssessment = new EventEmitter<Feedback>();
    @Input() public disabled = false;

    public emitChanges(): void {
        this.assessmentChange.emit(this.assessment);
    }

    public delete() {
        const confirmation = confirm(`Delete Assessment "${this.assessment.reference}"?`);
        if (confirmation) {
            this.deleteAssessment.emit(this.assessment);
        }
    }
}
