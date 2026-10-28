import { Component, effect, input, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * Displays a warning for instructors on submission page, team page and the assessment page.
 * Instructors are allowed to access and assess submissions before the exercise's due date is reached
 * and student submission would still be possible.
 */
@Component({
    selector: 'jhi-assessment-warning',
    templateUrl: './assessment-warning.component.html',
    imports: [FaIconComponent, TranslateDirective],
})
export class AssessmentWarningComponent {
    readonly exercise = input.required<Exercise>();
    readonly submissions = input<Submission[]>([]);

    readonly isBeforeExerciseDueDate = signal<boolean>(false);
    readonly showWarning = signal<boolean>(false);

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    constructor() {
        effect(() => {
            const exercise = this.exercise();
            const submissions = this.submissions();
            if (exercise.dueDate) {
                const now = dayjs();
                this.isBeforeExerciseDueDate.set(now.isBefore(exercise.dueDate));
                this.showWarning.set(now.isBefore(this.getLatestDueDate()) && !this.isFeedbackRequestSubmission(submissions));
            } else {
                this.isBeforeExerciseDueDate.set(false);
                this.showWarning.set(false);
            }
        });
    }

    /**
     * A course-wide Athena formative-feedback toggle does not prove that a given submission is an actual feedback request.
     * Only suppress the warning when a submission carries an AUTOMATIC_ATHENA result, i.e. it was legitimately created before the due date via a feedback request.
     */
    private isFeedbackRequestSubmission(submissions: Submission[]): boolean {
        return submissions.some((submission) => submission.results?.some((result) => result?.assessmentType === AssessmentType.AUTOMATIC_ATHENA));
    }

    private getLatestDueDate(): dayjs.Dayjs | undefined {
        return this.submissions()
            .map((submission) => submission.participation?.individualDueDate)
            .reduce((latest, next) => (next && next.isAfter(latest) ? next : latest), this.exercise().dueDate);
    }
}
