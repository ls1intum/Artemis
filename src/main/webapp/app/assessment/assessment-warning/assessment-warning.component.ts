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
            <div class="card-header" *ngIf="showWarning">
                <fa-icon [icon]="faExclamationTriangle" size="2x" class="text-warning" placement="bottom auto"></fa-icon>
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
    showWarning = false;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    /**
     * Checks if the due date of the exercise is over
     */
    ngOnChanges(): void {
        if (this.exercise.dueDate) {
            const now = dayjs();
            this.isBeforeExerciseDueDate = now.isBefore(this.exercise.dueDate);
            this.showWarning = now.isBefore(this.getLatestDueDate()) && !this.exercise.allowManualFeedbackRequests;
        }
    }

    private getLatestDueDate(): dayjs.Dayjs | undefined {
        return this.submissions
            .map((submission) => submission.participation?.individualDueDate)
            .reduce((latest, next) => (next && next.isAfter(latest) ? next : latest), this.exercise.dueDate);
    }
}
