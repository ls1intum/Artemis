import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-lecture-update-wizard-period',
    templateUrl: './lecture-wizard-period.component.html',
})
export class LectureUpdateWizardPeriodComponent {
    @Input() currentStep: number;
    @Input() lecture: Lecture;
    @Input() isEndDateBeforeStartDate: boolean;
    @Output() valueChange: EventEmitter<boolean> = new EventEmitter();

    isInvalidStartDate = false;
    isInvalidEndDate = false;

    constructor() {}

    /**
     * emits in case the end date is set before the start date, informing the parent component about this invalid change
     */
    emitDateConfigurationValidity() {
        if (this.isInvalidStartDate || this.isInvalidEndDate) {
            this.valueChange.emit(true);
        } else {
            this.valueChange.emit(false);
        }
    }

    setIsInvalidStartDateAndEmitDateConfigValidity(isInvalidDate: boolean) {
        this.isInvalidStartDate = isInvalidDate;
        this.emitDateConfigurationValidity();
    }

    setIsInvalidEndDateAndEmitDateConfigValidity(isInvalidDate: boolean) {
        this.isInvalidEndDate = isInvalidDate;
        this.emitDateConfigurationValidity();
    }
}
