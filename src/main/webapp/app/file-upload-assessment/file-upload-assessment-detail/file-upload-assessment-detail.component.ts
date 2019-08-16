import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Feedback } from 'app/entities/feedback';

@Component({
    selector: 'jhi-file-upload-assessment-detail',
    templateUrl: './file-upload-assessment-detail.component.html',
    styleUrls: [],
})
export class FileUploadAssessmentDetailComponent {
    @Input() public index: number;
    @Input() public assessment: Feedback;
    @Output() public assessmentChange = new EventEmitter<Feedback>();
    @Output() public deleteAssessment = new EventEmitter<Feedback>();
    @Input() public disabled = false;

    public emitChanges(): void {
        this.assessmentChange.emit(this.assessment);
    }

    public delete() {
        const confirmation = confirm(`Delete Assessment "${this.assessment.id}"?`);
        if (confirmation) {
            this.deleteAssessment.emit(this.assessment);
        }
    }
}
