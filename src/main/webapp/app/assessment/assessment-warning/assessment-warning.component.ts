import { Component, Input, OnChanges } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/entities/exercise.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { Submission } from 'app/entities/submission.model';

/**
 * Displays a warning for instructors on submission page, team page and the assessment page.
 * Instructors are allowed to access and assess submissions before the exercise's due date is reached
 * and student submission would still be possible.
 */
@Component({
    selector: 'jhi-assessment-warning',
    template: `
        <h6>
            <div class="card-header" *ngIf="isBeforeLatestDueDate">
                <fa-icon [icon]="faExclamationTriangle" size="2x" class="text-warning" placement="bottom"></fa-icon>
                <span *ngIf="isBeforeExerciseDueDate">{{ 'artemisApp.assessment.dashboard.warning' | artemisTranslate }}</span>
                <span *ngIf="!isBeforeExerciseDueDate">{{ 'artemisApp.assessment.dashboard.warningIndividual' | artemisTranslate }}</span>
            </div>
        </h6>
    `,
})
export class AssessmentWarningComponent implements OnChanges {
    @Input() exercise: Exercise;
    @Input() submissions: Submission[] = [];

    isBeforeExerciseDueDate = false;
    isBeforeLatestDueDate = false;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    /**
     * Checks if the due date of the exercise is over
     */
    ngOnChanges(): void {
        if (this.exercise.dueDate) {
            const now = dayjs();
            this.isBeforeExerciseDueDate = now.isBefore(this.exercise.dueDate);
            this.isBeforeLatestDueDate = now.isBefore(this.getLatestDueDate());
        }
    }

    private getLatestDueDate(): dayjs.Dayjs | undefined {
        return this.submissions
            .map((submission) => submission.participation?.individualDueDate)
            .reduce((latest, next) => {
                if (next && next.isAfter(latest)) {
                    return next;
                } else {
                    return latest;
                }
            }, this.exercise.dueDate);
    }
}
