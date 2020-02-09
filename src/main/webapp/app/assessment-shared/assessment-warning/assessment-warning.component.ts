import { Component, SimpleChanges, Input, OnChanges } from '@angular/core';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise';
/*
Warn instructors in the assessment and submission page
if the due date is not over yet as they are allowed to assess submissions there
 */
@Component({
    selector: 'jhi-assessment-warning',
    template: `
        <h6>
            <div class="card-header" *ngIf="this.isBeforeDueDate">
                <fa-icon icon="exclamation-triangle" size="2x" class="text-warning" placement="bottom"></fa-icon>
                {{ 'artemisApp.assessment.dashboard.warning' | translate }}
            </div>
        </h6>
    `,
})
export class AssessmentWarningComponent implements OnChanges {
    @Input() exercise: Exercise;
    currentDate: moment.MomentInput;
    isBeforeDueDate = false;

    ngOnChanges(changes: SimpleChanges): void {
        const dueDate = this.exercise.dueDate;
        if (dueDate != null) {
            this.isBeforeDueDate = dueDate.isAfter(this.currentDate);
        }
    }
}
