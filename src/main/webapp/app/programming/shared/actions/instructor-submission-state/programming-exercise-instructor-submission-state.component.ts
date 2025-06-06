import { Component, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { debounceTime, map, tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { faCircleNotch, faClock, faRedo } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ProgrammingExerciseTriggerAllButtonComponent } from '../trigger-all-button/programming-exercise-trigger-all-button.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DurationPipe } from 'app/shared/pipes/duration.pipe';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { hasExerciseChanged } from 'app/exercise/util/exercise.utils';

/**
 * This components provides two buttons to the instructor to interact with the students' submissions:
 * - Trigger builds for all student participations
 * - Trigger builds for failed student participations
 *
 * Also shows an info section next to the buttons about the number of building and failed submissions.
 */
@Component({
    selector: 'jhi-programming-exercise-instructor-submission-state',
    templateUrl: './programming-exercise-instructor-submission-state.component.html',
    imports: [FaIconComponent, NgbTooltip, NgClass, ProgrammingExerciseTriggerAllButtonComponent, ButtonComponent, ArtemisTranslatePipe, DurationPipe],
})
export class ProgrammingExerciseInstructorSubmissionStateComponent implements OnChanges, OnInit {
    private programmingSubmissionService = inject(ProgrammingSubmissionService);

    FeatureToggle = FeatureToggle;
    ButtonType = ButtonType;
    ProgrammingSubmissionState = ProgrammingSubmissionState;

    @Input() exercise: ProgrammingExercise;

    hasFailedSubmissions = false;
    hasBuildingSubmissions = false;
    buildingSummary: { [submissionState: string]: number };
    isBuildingFailedSubmissions = false;

    resultEtaInMs: number;

    submissionStateSubscription: Subscription;
    resultEtaSubscription: Subscription;

    // Icons
    faClock = faClock;
    faCircleNotch = faCircleNotch;
    faRedo = faRedo;

    ngOnInit(): void {
        this.resultEtaSubscription = this.programmingSubmissionService.getResultEtaInMs().subscribe((resultEta) => (this.resultEtaInMs = resultEta));
    }

    /**
     * When the selected exercise changes, create a subscription to the complete submission state of the exercise.
     *
     * @param changes only relevant for change of exerciseId.
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasExerciseChanged(changes)) {
            this.submissionStateSubscription = this.programmingSubmissionService
                .getSubmissionStateOfExercise(this.exercise.id!)
                .pipe(
                    map(this.sumSubmissionStates),
                    // If we would update the UI with every small change, it would seem very hectic. So we always take the latest value after 1 second.
                    debounceTime(500),
                    tap((buildingSummary: { [submissionState: string]: number }) => {
                        this.buildingSummary = buildingSummary;
                        this.hasFailedSubmissions = this.buildingSummary[ProgrammingSubmissionState.HAS_FAILED_SUBMISSION] > 0;
                        this.hasBuildingSubmissions = this.buildingSummary[ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION] > 0;
                    }),
                )
                .subscribe();
        }
    }

    /**
     * Retrieve the participation ids that have a failed submission and retry their build.
     */
    triggerBuildOfFailedSubmissions() {
        this.isBuildingFailedSubmissions = true;
        const failedSubmissionParticipations = this.programmingSubmissionService.getSubmissionCountByType(this.exercise.id!, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        this.programmingSubmissionService
            .triggerInstructorBuildForParticipationsOfExercise(this.exercise.id!, failedSubmissionParticipations)
            .subscribe(() => (this.isBuildingFailedSubmissions = false));
    }

    private sumSubmissionStates = (buildState: ExerciseSubmissionState) =>
        Object.values(buildState).reduce((acc: { [state: string]: number }, { submissionState }) => {
            return { ...acc, [submissionState]: (acc[submissionState] || 0) + 1 };
        }, {});
}
