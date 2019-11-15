import { Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { hasParticipationChanged, InitializationState, Participation } from 'app/entities/participation';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { SubmissionType } from 'app/entities/submission';
import { HttpResponse } from '@angular/common/http';
import { FeatureToggle } from 'app/feature-toggle';

/**
 * Component for triggering a build for the CURRENT submission of the student (does not create a new commit!).
 * The participation given as input needs to have the results attached as this component checks if there is at least one result.
 * If there is no result, the button is disabled because this would mean that the student has not made a commit yet.
 */
export abstract class ProgrammingExerciseTriggerBuildButtonComponent implements OnChanges, OnDestroy {
    FeatureToggle = FeatureToggle;
    ButtonType = ButtonType;

    @Input() exercise: ProgrammingExercise;
    @Input() participation: Participation;
    @Input() btnSize = ButtonSize.SMALL;

    participationBuildCanBeTriggered: boolean;
    participationHasLatestSubmissionWithoutResult: boolean;
    isRetrievingBuildStatus: boolean;
    isBuilding: boolean;
    // If true, the trigger button is also displayed for successful submissions.
    showForSuccessfulSubmissions = false;

    private submissionSubscription: Subscription;
    private resultSubscription: Subscription;

    protected constructor(protected submissionService: ProgrammingSubmissionService) {}

    /**
     * Check if the participation has changed, if so set up the websocket connections.
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            // We can trigger the build only if the participation is active (has build plan) or if the build plan was archived (new build plan will be created).
            this.participationBuildCanBeTriggered =
                this.participation.initializationState === InitializationState.INITIALIZED || this.participation.initializationState === InitializationState.INACTIVE;
            if (this.participationBuildCanBeTriggered) {
                this.setupSubmissionSubscription();
            }
        }
    }

    ngOnDestroy(): void {
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
    }

    /**
     * Set up a websocket subscription to pending submissions to set the isBuilding flag.
     * If there is a pending submission, isBuilding is set to true, otherwise to false.
     */
    setupSubmissionSubscription() {
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        this.submissionSubscription = this.submissionService
            .getLatestPendingSubmissionByParticipationId(this.participation.id, this.exercise.id)
            .pipe(
                tap(({ submissionState }) => {
                    switch (submissionState) {
                        case ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION:
                            this.isBuilding = false;
                            this.participationHasLatestSubmissionWithoutResult = false;
                            break;
                        case ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION:
                            this.isBuilding = true;
                            break;
                        case ProgrammingSubmissionState.HAS_FAILED_SUBMISSION:
                            this.participationHasLatestSubmissionWithoutResult = true;
                            this.isBuilding = false;
                            break;
                    }
                }),
            )
            .subscribe();
    }

    /**
     * Trigger a regular build or a failed build, depending on the state of the latest submission.
     *
     * @param submissionType that is used for the creation of the submission.
     */
    triggerBuild(submissionType: SubmissionType) {
        this.isRetrievingBuildStatus = true;
        if (this.participationHasLatestSubmissionWithoutResult) {
            this.submissionService.triggerFailedBuild(this.participation.id).subscribe(() => (this.isRetrievingBuildStatus = false));
        } else {
            this.submissionService.triggerBuild(this.participation.id, submissionType).subscribe(() => (this.isRetrievingBuildStatus = false));
        }
    }
}
