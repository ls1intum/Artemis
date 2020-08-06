import { Component, Input, OnInit } from '@angular/core';
import * as moment from 'moment';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-lifecycle',
    templateUrl: './programming-exercise-lifecycle.component.html',
    styleUrls: ['./programming-exercise-test-schedule-picker.scss'],
})
export class ProgrammingExerciseLifecycleComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;
    @Input() isExamMode: boolean;

    readonly assessmentType = AssessmentType;

    constructor(private translator: TranslateService) {}

    /**
     * If the programming exercise does not have an id, set the assessment Type to AUTOMATIC
     */
    ngOnInit(): void {
        if (!this.exercise.id) {
            this.exercise.assessmentType = AssessmentType.AUTOMATIC;
        }
    }

    /**
     * Toggles the assessment type between AUTOMATIC (only tests in repo will be run using build plans) and
     * SEMI_AUTOMATIC (After all automatic tests have been run, the tutors will have to make a final manual assessment)
     *
     */
    toggleHasManualAssessment() {
        this.exercise.assessmentType = this.exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC ? AssessmentType.AUTOMATIC : AssessmentType.SEMI_AUTOMATIC;
        // when the new value is AssessmentType.AUTOMATIC, we need to reset assessment due date
        if (this.exercise.assessmentType === AssessmentType.AUTOMATIC) {
            this.exercise.assessmentDueDate = null;
        }
    }

    /**
     * Sets the new release date and updates "due date" and "after due date" if the release date is after the due date
     *
     * @param newReleaseDate The new release date
     */
    updateReleaseDate(newReleaseDate: moment.Moment | null) {
        if (this.exercise.dueDate && newReleaseDate && moment(newReleaseDate).isAfter(this.exercise.dueDate)) {
            this.updateDueDate(newReleaseDate);
        }
        this.exercise.releaseDate = newReleaseDate;
    }

    /**
     * Updates the due Date of the programming exercise
     * @param dueDate the new dueDate
     */
    private updateDueDate(dueDate: moment.Moment) {
        alert(this.translator.instant('artemisApp.programmingExercise.timeline.alertNewDueDate'));
        this.exercise.dueDate = dueDate;

        // If the new due date is after the "After Due Date", then we have to set the "After Due Date" to the new due date
        const afterDue = this.exercise.buildAndTestStudentSubmissionsAfterDueDate;
        if (afterDue && moment(dueDate).isAfter(afterDue)) {
            this.exercise.buildAndTestStudentSubmissionsAfterDueDate = dueDate;
            alert(this.translator.instant('artemisApp.programmingExercise.timeline.alertNewAfterDueDate'));
        }
    }
}
