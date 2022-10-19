import { Component, Input } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-lecture-update-wizard-period',
    templateUrl: './lecture-wizard-period.component.html',
})
export class LectureUpdateWizardPeriodComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;

    constructor() {}
}
