import { Component, Input, OnInit, OnChanges } from '@angular/core';
import dayjs from 'dayjs';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-lifecycle',
    templateUrl: './programming-exercise-lifecycle.component.html',
    styleUrls: ['./programming-exercise-test-schedule-picker.scss'],
})
export class ProgrammingExerciseLifecycleComponent implements OnInit, OnChanges {
    @Input() exercise: ProgrammingExercise;
    @Input() isExamMode: boolean;
    @Input() readOnly: boolean;
    minTestExecutionDate = dayjs();

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
     * Sets a proper minimum date for executing automatic tests depending on if its exam mode or not.
     */
    ngOnChanges(): void {
        let testExecutionDate;
        if (this.isExamMode) {
            testExecutionDate = this.exercise.exerciseGroup?.exam?.endDate;
        } else {
            testExecutionDate = this.exercise.dueDate;
        }
        // If no due date has been set for the exam or the exercise we set the current date as minimum to prevent execution dates in the past
        this.minTestExecutionDate = testExecutionDate ?? dayjs();
    }

    /**
     * Toggles the assessment type between AUTOMATIC (only tests in repo will be run using build plans) and
     * SEMI_AUTOMATIC (After all automatic tests have been run, the tutors will have to make a final manual assessment)
     *
     */
    toggleAssessmentType() {
        if (this.exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC) {
            this.exercise.assessmentType = AssessmentType.AUTOMATIC;
            if (this.isExamMode || this.exercise.course?.complaintsEnabled) {
                this.exercise.allowComplaintsForAutomaticAssessments = true;
            } else {
                this.exercise.allowComplaintsForAutomaticAssessments = false;
            }
        } else if (this.exercise.allowComplaintsForAutomaticAssessments) {
            this.exercise.allowComplaintsForAutomaticAssessments = false;
        } else {
            this.exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            this.exercise.allowComplaintsForAutomaticAssessments = false;
        }

        // when the new value is AssessmentType.AUTOMATIC, we need to reset assessment due date
        if (this.exercise.assessmentType === AssessmentType.AUTOMATIC) {
            this.exercise.assessmentDueDate = undefined;
        }
    }

    /**
     * Sets the new release date and updates "due date" and "after due date" if the release date is after the due date
     *
     * @param newReleaseDate The new release date
     */
    updateReleaseDate(newReleaseDate?: dayjs.Dayjs) {
        if (this.exercise.dueDate && newReleaseDate && dayjs(newReleaseDate).isAfter(this.exercise.dueDate)) {
            this.updateDueDate(newReleaseDate);
        }
        this.exercise.releaseDate = newReleaseDate;
    }

    /**
     * Updates the due Date of the programming exercise
     * @param dueDate the new dueDate
     */
    private updateDueDate(dueDate: dayjs.Dayjs) {
        alert(this.translator.instant('artemisApp.programmingExercise.timeline.alertNewDueDate'));
        this.exercise.dueDate = dueDate;

        // If the new due date is after the "After Due Date", then we have to set the "After Due Date" to the new due date
        const afterDue = this.exercise.buildAndTestStudentSubmissionsAfterDueDate;
        if (afterDue && dayjs(dueDate).isAfter(afterDue)) {
            this.exercise.buildAndTestStudentSubmissionsAfterDueDate = dueDate;
            alert(this.translator.instant('artemisApp.programmingExercise.timeline.alertNewAfterDueDate'));
        }
    }
}
