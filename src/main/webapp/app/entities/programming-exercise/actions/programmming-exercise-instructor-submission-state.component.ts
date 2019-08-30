import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { map, tap } from 'rxjs/operators';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { Subscription } from 'rxjs';

/**
 * This components provides two buttons to the instructor to interact with the students' submissions:
 * - Trigger builds for all student participations
 * - Trigger builds for failed student participations
 *
 * Also shows an info section next to the buttons about the number of building and failed submissions.
 */
@Component({
    selector: 'jhi-programming-exercise-instructor-submission-state',
    templateUrl: './programmming-exercise-instructor-submission-state.component.html',
})
export class ProgrammmingExerciseInstructorSubmissionStateComponent implements OnChanges {
    ProgrammingSubmissionState = ProgrammingSubmissionState;

    @Input() exerciseId: number;

    hasFailedSubmissions = false;
    buildingSummary: { [submissionState: string]: number };
    isBuildingFailedSubmissions = false;

    submissionStateSubscription: Subscription;

    constructor(private programmingSubmissionService: ProgrammingSubmissionService) {}

    /**
     * When the selected exercise changes, create a subscription to the complete submission state of the exercise.
     *
     * @param changes only relevant for change of exerciseId.
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.exerciseId && !!changes.exerciseId.currentValue) {
            this.submissionStateSubscription = this.programmingSubmissionService
                .getSubmissionStateOfExercise(this.exerciseId)
                .pipe(
                    map(this.sumSubmissionStates),
                    tap((buildingSummary: { [submissionState: string]: number }) => {
                        this.buildingSummary = buildingSummary;
                        this.hasFailedSubmissions = this.buildingSummary[ProgrammingSubmissionState.HAS_FAILED_SUBMISSION] > 0;
                    }),
                )
                .subscribe();
        }
    }

    triggerBuildOfAllSubmissions() {
        this.programmingSubmissionService.triggerInstructorBuildForAllParticipationsOfExercise(this.exerciseId).subscribe();
    }

    /**
     * Retrieve the participation ids that have a failed submission and retry their build.
     */
    triggerBuildOfFailedSubmissions() {
        this.isBuildingFailedSubmissions = true;
        const failedSubmissionParticipations = this.programmingSubmissionService.getFailedSubmissionParticipationsForExercise(this.exerciseId);
        this.programmingSubmissionService
            .triggerInstructorBuildForParticipationsOfExercise(this.exerciseId, failedSubmissionParticipations)
            .pipe()
            .subscribe(() => (this.isBuildingFailedSubmissions = false));
    }

    private sumSubmissionStates = (buildState: ExerciseSubmissionState) =>
        Object.values(buildState).reduce((acc: { [state: string]: number }, [submissionState]) => {
            return { ...acc, [submissionState]: (acc[submissionState] || 0) + 1 };
        }, {});
}
