import { Component, SimpleChanges, Input, OnChanges } from '@angular/core';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-assessment-warning',
    templateUrl: './assessment-warning.component.html',
})
export class AssessmentWarningComponent implements OnChanges {
    @Input()
    exercise: Exercise;
    currentDate: moment.MomentInput;
    isBeforeDueDate = false;

    ngOnChanges(changes: SimpleChanges): void {
        const dueDate = this.exercise.dueDate;
        if (dueDate != null) {
            this.isBeforeDueDate = dueDate.isAfter(this.currentDate);
        }
    }
}
