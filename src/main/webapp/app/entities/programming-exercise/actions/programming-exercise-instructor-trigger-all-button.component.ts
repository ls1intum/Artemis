import { Input, Component, OnChanges, SimpleChanges } from '@angular/core';
import { tap, map, reduce } from 'rxjs/operators';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ExerciseBuildState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { hasExerciseChanged } from 'app/entities/exercise';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-all-button',
    templateUrl: './programming-exercise-instructor-trigger-all-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerAllButtonComponent implements OnChanges {
    ProgrammingSubmissionState = ProgrammingSubmissionState;
    @Input() exerciseId: number;
    isBuilding = false;
    buildingSummary: { [submissionState: string]: number };

    constructor(private programmingSubmissionService: ProgrammingSubmissionService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (hasExerciseChanged(changes)) {
            this.programmingSubmissionService
                .subscribeSubmissionStateOfExercise(this.exerciseId)
                .pipe(
                    map(Object.values),
                    reduce((acc, [submissionState]) => {
                        return { ...acc, [submissionState]: (acc[submissionState] || 0) + 1 };
                    }, {}),
                    tap(buildingSummary => {
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
