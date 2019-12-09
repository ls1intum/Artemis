import { Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { filter, tap, catchError } from 'rxjs/operators';
import { Subscription, of } from 'rxjs';
import { compose, head, orderBy } from 'lodash/fp';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { hasParticipationChanged, InitializationState, Participation, ParticipationWebsocketService } from 'app/entities/participation';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { ButtonSize, ButtonType } from 'app/shared/components';
import { SubmissionType } from 'app/entities/submission';
import { AssessmentType } from 'app/entities/assessment-type';
import { hasDeadlinePassed } from 'app/entities/programming-exercise/utils/programming-exercise.utils';
import { FeatureToggle } from 'app/feature-toggle';
import { Result } from 'app/entities/result';
import { JhiAlertService } from 'ng-jhipster';

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
    // This only works correctly when the provided participation includes its latest result.
    lastResultIsManual: boolean;
    participationHasLatestSubmissionWithoutResult: boolean;
    isRetrievingBuildStatus: boolean;
    isBuilding: boolean;
    // If true, the trigger button is also displayed for successful submissions.
    showForSuccessfulSubmissions = false;

    private submissionSubscription: Subscription;
    private resultSubscription: Subscription;

    protected constructor(
        protected submissionService: ProgrammingSubmissionService,
        protected participationWebsocketService: ParticipationWebsocketService,
        protected alertService: JhiAlertService,
    ) {}

    /**
     * Check if the participation has changed, if so set up the websocket connections.
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (hasParticipationChanged(changes)) {
            // The identification of manual results is only relevant when the deadline was passed, otherwise they could be overridden anyway.
            if (hasDeadlinePassed(this.exercise)) {
                // If the last result was manual, the instructor might not want to override it with a new automatic result.
                const newestResult = !!this.participation.results && compose(head, orderBy('id', 'desc'))(this.participation.results);
                this.lastResultIsManual = !!newestResult && newestResult.assessmentType === AssessmentType.MANUAL;
            }
            // We can trigger the build only if the participation is active (has build plan) or if the build plan was archived (new build plan will be created).
            this.participationBuildCanBeTriggered =
                this.participation.initializationState === InitializationState.INITIALIZED || this.participation.initializationState === InitializationState.INACTIVE;
            if (this.participationBuildCanBeTriggered) {
                this.setupSubmissionSubscription();
                this.setupResultSubscription();
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
     * Set up a websocket subscription to incoming results.
     * The subscription is used to determine the type of the latest result.
     */
    setupResultSubscription() {
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id)
            .pipe(
                filter(result => !!result),
                tap((result: Result) => {
                    this.lastResultIsManual = !!result && result.assessmentType === AssessmentType.MANUAL;
                }),
            )
            .subscribe();
    }

    abstract triggerBuild(submissionType: SubmissionType): void;

    triggerWithType(submissionType: SubmissionType) {
        this.isRetrievingBuildStatus = true;
        return this.submissionService.triggerBuild(this.participation.id, submissionType).pipe(tap(() => (this.isRetrievingBuildStatus = false)));
    }

    triggerFailed(lastGraded = false) {
        this.isRetrievingBuildStatus = true;
        return this.submissionService.triggerFailedBuild(this.participation.id, lastGraded).pipe(tap(() => (this.isRetrievingBuildStatus = false)));
    }
}
