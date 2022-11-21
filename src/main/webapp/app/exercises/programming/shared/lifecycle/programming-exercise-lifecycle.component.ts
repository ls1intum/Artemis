import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { faCogs, faHandshake, faHandshakeSlash, faUserCheck, faUserSlash, faLink, faLinkSlash } from '@fortawesome/free-solid-svg-icons';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { IncludedInOverallScore } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-programming-exercise-lifecycle',
    templateUrl: './programming-exercise-lifecycle.component.html',
    styleUrls: ['./programming-exercise-test-schedule-picker.scss'],
})
export class ProgrammingExerciseLifecycleComponent implements OnInit, OnChanges {
    @Input() exercise: ProgrammingExercise;
    @Input() isExamMode: boolean;
    @Input() readOnly: boolean;

    readonly assessmentType = AssessmentType;
    readonly IncludedInOverallScore = IncludedInOverallScore;

    // Icons
    faCogs = faCogs;
    faUserCheck = faUserCheck;
    faUserSlash = faUserSlash;
    faHandshake = faHandshake;
    faHandshakeSlash = faHandshakeSlash;
    faLink = faLink;
    faLinkSlash = faLinkSlash;

    constructor(private translator: TranslateService, private exerciseService: ExerciseService) {}

    /**
     * If the programming exercise does not have an id, set the assessment Type to AUTOMATIC
     */
    ngOnInit(): void {
        if (!this.exercise.id) {
            this.exercise.assessmentType = AssessmentType.AUTOMATIC;
        }
    }

    ngOnChanges(simpleChanges: SimpleChanges) {
        if (simpleChanges.exercise) {
            const newExercise = simpleChanges.exercise.currentValue;
            if (this.exerciseService.hasDueDateError(newExercise)) {
                // Checking for due date errors and ordering the calls to avoid updating exampleSolutionPublicationDate twice.
                this.updateReleaseDate(newExercise.releaseDate);
                this.updateExampleSolutionPublicationDate(newExercise.dueDate);
            } else {
                this.updateExampleSolutionPublicationDate(newExercise.dueDate);
                this.updateReleaseDate(newExercise.releaseDate);
            }
        }
    }

    toggleManualFeedbackRequests() {
        this.exercise.allowManualFeedbackRequests = !this.exercise.allowManualFeedbackRequests;
        if (this.exercise.allowManualFeedbackRequests) {
            this.exercise.assessmentDueDate = undefined;
            this.exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        }
    }

    /**
     * Toggles the assessment type between AUTOMATIC (only tests in repo will be run using build plans) and
     * SEMI_AUTOMATIC (After all automatic tests have been run, the tutors will have to make a final manual assessment)
     *
     */
    toggleAssessmentType() {
        if (this.exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC) {
            this.exercise.assessmentType = AssessmentType.AUTOMATIC;
            this.exercise.assessmentDueDate = undefined;
        } else {
            this.exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            this.exercise.allowComplaintsForAutomaticAssessments = false;
        }
    }

    /**
     * Toggles the value for allowing complaints for automatic assessment between true and false
     */
    toggleComplaintsType() {
        this.exercise.allowComplaintsForAutomaticAssessments = !this.exercise.allowComplaintsForAutomaticAssessments;
    }

    /**
     * Toggles the value for allowing complaints for automatic assessment between true and false
     */
    toggleReleaseTests() {
        this.exercise.releaseTestsWithExampleSolution = !this.exercise.releaseTestsWithExampleSolution;
    }

    /**
     * Sets the new release date and updates "due date" and "after due date" if the release date is after the due date
     * Does not propagate changes to dates other than release date if readOnly is true.
     *
     * @param newReleaseDate The new release date
     */
    updateReleaseDate(newReleaseDate?: dayjs.Dayjs) {
        this.exercise.releaseDate = newReleaseDate;
        if (this.readOnly) {
            // Changes from parent component are allowed but no cascading changes should be made in read-only mode.
            return;
        }
        if (this.exerciseService.hasDueDateError(this.exercise)) {
            this.updateDueDate(newReleaseDate!);
        }
        this.updateExampleSolutionPublicationDate(newReleaseDate);
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

    /**
     * Updates the example solution publication date of the programming exercise if it is set and not after release or due date.
     * Due date check is not performed if exercise is not included in the grade.
     * This method is a no-op if readOnly is true.
     *
     * @param newReleaseOrDueDate the new exampleSolutionPublicationDate if it is after the current exampleSolutionPublicationDate
     */
    updateExampleSolutionPublicationDate(newReleaseOrDueDate?: dayjs.Dayjs) {
        if (!this.readOnly && this.exerciseService.hasExampleSolutionPublicationDateError(this.exercise)) {
            const message = dayjs(newReleaseOrDueDate).isSame(this.exercise.dueDate)
                ? 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate'
                : 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsReleaseDate';
            alert(this.translator.instant(message));
            this.exercise.exampleSolutionPublicationDate = newReleaseOrDueDate;
        }
    }
}
