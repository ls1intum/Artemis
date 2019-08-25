/* angular */
import { Component, EventEmitter, Input, Output } from '@angular/core';

/* application */
import { Feedback } from 'app/entities/feedback';

/* 3rd party */
import { TranslateService } from '@ngx-translate/core';

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

    constructor(private translateService: TranslateService) {}
    public emitChanges(): void {
        this.assessmentChange.emit(this.assessment);
    }

    public delete() {
        const confirmation = confirm(this.translateService.instant('artemisApp.fileUploadAssessment.deleteAssessment', { index: this.assessment.id }));
        if (confirmation) {
            this.deleteAssessment.emit(this.assessment);
        }
    }
}
