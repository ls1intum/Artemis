import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TextAssessment } from 'app/entities/text-assessments/text-assessments.model';
import { Color } from '../highlight-colors';

@Component({
    selector: 'jhi-text-assessment-detail',
    templateUrl: './text-assessment-detail.component.html',
    styleUrls: ['./text-assessment-detail.component.scss']
})
export class TextAssessmentDetailComponent {
    @Input() public assessment: TextAssessment;
    @Output() public assessmentChange = new EventEmitter<TextAssessment>();
    @Input() public highlightColor: Color;
    @Output() public deleteAssessment = new EventEmitter<TextAssessment>();

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
