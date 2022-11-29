import { Component, Input } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-lecture-update-wizard-attachments',
    templateUrl: './lecture-wizard-attachments.component.html',
})
export class LectureUpdateWizardAttachmentsComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;

    constructor() {}
}
