import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-lecture-update-wizard-period',
    templateUrl: './lecture-wizard-period.component.html',
})
export class LectureUpdateWizardPeriodComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;
    @Input() validateDatesFunction: () => void;
    @Input() isEndDateBeforeStartDate: boolean;
    @Output() invalidChange = new EventEmitter();

    constructor() {}

    /**
     * emits in case the end date is set before the start date, informing the parent component about this invalid change
     */
    emitInvalidDateConfiguration() {
        if (this.isEndDateBeforeStartDate) {
            this.invalidChange.emit();
        }
    }
}
