import { Component, Input, OnChanges } from '@angular/core';
import dayjs from 'dayjs';
import { Exercise } from 'app/entities/exercise.model';

/*
Display warning for instructors on submission page, team page and the assessment page.
Instructors are allowed to access and assess submissions before the exercise's due date is reached
and student submission would still be possible.
 */
@Component({
    selector: 'jhi-assessment-warning',
    template: `
        <h6>
            <div class="card-header" *ngIf="isBeforeDueDate">
                <fa-icon [icon]="'exclamation-triangle'" size="2x" class="text-warning" placement="bottom"></fa-icon>
                {{ 'artemisApp.assessment.dashboard.warning' | artemisTranslate }}
            </div>
        </h6>
    `,
})
export class AssessmentWarningComponent implements OnChanges {
    @Input() exercise: Exercise;
    isBeforeDueDate = false;

    /**
     * Checks if the due date of the exercise is over
     */
    ngOnChanges(): void {
        const dueDate = this.exercise.dueDate;
        if (dueDate != undefined) {
            this.isBeforeDueDate = dayjs().isBefore(dueDate);
        }
    }
}
