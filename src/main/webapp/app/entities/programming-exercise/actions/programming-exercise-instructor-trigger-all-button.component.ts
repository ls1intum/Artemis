import { Input, Component, OnChanges, SimpleChanges } from '@angular/core';
import { tap, map, reduce } from 'rxjs/operators';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ExerciseBuildState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { hasExerciseChanged } from 'app/entities/exercise';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-all-button',
    templateUrl: './programming-exercise-instructor-trigger-all-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerAllButtonComponent implements OnChanges {
    ProgrammingSubmissionState = ProgrammingSubmissionState;

    @Input() exerciseId: number;

    isBuilding = false;
    buildingSummary: { [submissionState: string]: number };
    submissionStateSubscription: Subscription;

    constructor(private programmingSubmissionService: ProgrammingSubmissionService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.exerciseId && !!changes.exerciseId.currentValue) {
            this.submissionStateSubscription = this.programmingSubmissionService
                .getSubmissionStateOfExercise(this.exerciseId)
                .pipe(
                    map(buildState =>
                        Object.values(buildState).reduce((acc: { [state: string]: number }, [submissionState]) => {
                            return { ...acc, [submissionState]: (acc[submissionState] || 0) + 1 };
                        }, {}),
                    ),
                    tap((buildingSummary: { [submissionState: string]: number }) => {
                        this.buildingSummary = buildingSummary;
                        this.isBuilding = this.buildingSummary[ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION] > 0;
                    }),
                )
                .subscribe();
        }
    }

    triggerBuild() {
        this.programmingSubmissionService
            .triggerInstructorBuildForAllParticipationsOfExercise(this.exerciseId)
            .pipe(tap(() => (this.isBuilding = true)))
            .subscribe();
    }
}
