import { Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { hasParticipationChanged, InitializationState, Participation } from 'app/entities/participation';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { ButtonSize, ButtonType } from 'app/shared/components';

/**
 * Component for triggering a build for the CURRENT submission of the student (does not create a new commit!).
 * The participation given as input needs to have the results attached as this component checks if there is at least one result.
 * If there is no result, the button is disabled because this would mean that the student has not made a commit yet.
 */
export abstract class ProgrammingExerciseTriggerBuildButtonComponent implements OnChanges, OnDestroy {
    ButtonType = ButtonType;
    abstract triggerBuild: (event: any) => void;

    @Input() exercise: ProgrammingExercise;
    @Input() participation: Participation;
    @Input() btnSize = ButtonSize.SMALL;

    participationIsActive: boolean;
    participationHasLatestSubmissionWithoutResult: boolean;
    isBuilding: boolean;
    alwaysShowTriggerButton: boolean;

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
            this.participationIsActive = this.participation.initializationState === InitializationState.INITIALIZED;
            if (this.participationIsActive) {
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
}
