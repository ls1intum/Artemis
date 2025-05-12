import { Component, OnChanges, input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Displays a warning for instructors on submission page, team page and the assessment page.
 * Instructors are allowed to access and assess submissions before the exercise's due date is reached
 * and student submission would still be possible.
 */
@Component({
    selector: 'jhi-assessment-warning',
    template: `
        <h6>
            @if (showWarning) {
                <div class="card-header">
                    <fa-icon [icon]="faExclamationTriangle" size="2x" class="text-warning" placement="bottom auto" />
                    @if (isBeforeExerciseDueDate) {
                        <span jhiTranslate="artemisApp.assessment.dashboard.warning"></span>
                    }
                    @if (!isBeforeExerciseDueDate) {
                        <span jhiTranslate="artemisApp.assessment.dashboard.warningIndividual"></span>
                    }
                </div>
            }
        </h6>
    `,
    imports: [FaIconComponent, TranslateDirective],
})
export class AssessmentWarningComponent implements OnChanges {
    readonly exercise = input.required<Exercise>();
    readonly submissions = input<Submission[]>([]);

    isBeforeExerciseDueDate = false;
    showWarning = false;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    /**
     * Checks if the due date of the exercise is over
     */
    ngOnChanges() {
        const exercise = this.exercise();
        if (exercise.dueDate) {
            const now = dayjs();
            this.isBeforeExerciseDueDate = now.isBefore(exercise.dueDate);
            this.showWarning = now.isBefore(this.getLatestDueDate()) && !exercise.allowFeedbackRequests;
        }
    }

    private getLatestDueDate(): dayjs.Dayjs | undefined {
        return this.submissions()
            .map((submission) => submission.participation?.individualDueDate)
            .reduce((latest, next) => (next && next.isAfter(latest) ? next : latest), this.exercise().dueDate);
    }
}
