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
    readonly assessmentType = AssessmentType;

    constructor(private translator: TranslateService) {}

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
    toggleHasManualTests() {
        this.exercise.assessmentType = this.exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC ? AssessmentType.AUTOMATIC : AssessmentType.SEMI_AUTOMATIC;
    }

    /**
     * Sets the new release date and updates teh due and after due date if the release date if they are before the
     * release date now.
     *
     * @param releaseDate The new release date
     */
    updateReleaseDate(releaseDate: moment.Moment | null) {
        if (this.exercise.dueDate && releaseDate && releaseDate.isAfter(this.exercise.dueDate)) {
            this.updateDueDate(releaseDate);
        }

        this.exercise.releaseDate = releaseDate;
    }

    private updateDueDate(dueDate: moment.Moment) {
        const afterDue = this.exercise.buildAndTestStudentSubmissionsAfterDueDate;
        alert(this.translator.instant('artemisApp.programmingExercise.timeline.alertNewDueDate'));
        this.exercise.dueDate = dueDate;

        // If the new due date is after the "After Due Date", then we have to set the "After Due Date" to the new due date
        if (afterDue && this.exercise.dueDate.isAfter(afterDue)) {
            this.exercise.buildAndTestStudentSubmissionsAfterDueDate = dueDate;
            alert(this.translator.instant('artemisApp.programmingExercise.timeline.alertNewAfterDueDate'));
        }
    }
}
