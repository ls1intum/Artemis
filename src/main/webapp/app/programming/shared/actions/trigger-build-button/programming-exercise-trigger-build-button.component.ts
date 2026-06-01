import { Component, OnDestroy, effect, inject, input, signal, untracked } from '@angular/core';
import { filter, finalize, tap } from 'rxjs/operators';
import { EMPTY, Subscription } from 'rxjs';
import { head, orderBy } from 'lodash-es';
import { InitializationState, Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming/shared/services/programming-submission.service';
import { hasDueDatePassed } from 'app/programming/shared/utils/programming-exercise.utils';
import { isManualResult } from 'app/exercise/result/result.utils';

/**
 * Component for triggering a build for the CURRENT submission of the student (does not create a new commit!).
 * The participation given as input needs to have the results attached as this component checks if there is at least one result.
 * If there is no result, the button is disabled because this would mean that the student has not made a commit yet.
 */
@Component({
    template: '',
})
export abstract class ProgrammingExerciseTriggerBuildButtonComponent implements OnDestroy {
    FeatureToggle = FeatureToggle;
    ButtonType = ButtonType;

    private submissionService = inject(ProgrammingSubmissionService);
    private participationWebsocketService = inject(ParticipationWebsocketService);

    readonly exercise = input.required<ProgrammingExercise>();
    // Optional: the button is rendered for repositories that may have no participation (e.g. an auxiliary
    // repository in the instructor editor binds `selectedParticipation!`, which is undefined at runtime).
    readonly participation = input<Participation>();
    readonly btnSize = input(ButtonSize.SMALL);
    readonly title = input('');

    readonly participationBuildCanBeTriggered = signal(false);
    // This only works correctly when the provided participation includes its latest result.
    readonly lastResultIsManual = signal(false);
    readonly participationHasLatestSubmissionWithoutResult = signal(false);
    readonly isRetrievingBuildStatus = signal(false);
    readonly isBuilding = signal(false);
    // If true, the trigger button is also displayed for successful submissions.
    readonly showForSuccessfulSubmissions = signal(false);

    private submissionSubscription: Subscription;
    private resultSubscription: Subscription;

    // True if the student triggers. false if an instructor triggers it
    protected personalParticipation: boolean;

    private previousParticipationId: number | undefined;

    constructor() {
        // React only to participation changes (keyed on id). The body is untracked so reads like exercise() don't
        // widen the effect's dependencies, and the per-participation subscriptions survive unrelated re-renders.
        effect(() => {
            const participation = this.participation();
            untracked(() => {
                // No participation (e.g. an auxiliary repository binds an undefined participation): reset and tear down.
                if (!participation?.id) {
                    this.previousParticipationId = undefined;
                    this.submissionSubscription?.unsubscribe();
                    this.resultSubscription?.unsubscribe();
                    this.participationBuildCanBeTriggered.set(false);
                    this.isBuilding.set(false);
                    this.participationHasLatestSubmissionWithoutResult.set(false);
                    return;
                }
                if (participation.id === this.previousParticipationId) {
                    return;
                }
                this.previousParticipationId = participation.id;

                // The identification of manual results is only relevant when the due date was passed, otherwise they could be overridden anyway.
                if (hasDueDatePassed(this.exercise())) {
                    // If the last result was manual, the instructor might not want to override it with a new automatic result.
                    const allResults = participation.submissions?.flatMap((submission) => submission.results ?? []) || [];
                    const newestResult = allResults.length ? head(orderBy(allResults, ['id'], ['desc'])) : undefined;
                    this.lastResultIsManual.set(!!newestResult && isManualResult(newestResult));
                }
                // We can trigger the build only if the participation is active (has build plan), if the build plan was archived (new build plan will be created)
                // or the due date is over.
                const canBeTriggered =
                    !!participation.initializationState &&
                    [InitializationState.INITIALIZED, InitializationState.INACTIVE, InitializationState.FINISHED].includes(participation.initializationState);
                this.participationBuildCanBeTriggered.set(canBeTriggered);
                if (canBeTriggered) {
                    this.setupSubmissionSubscription();
                    this.setupResultSubscription();
                } else {
                    this.submissionSubscription?.unsubscribe();
                    this.resultSubscription?.unsubscribe();
                    this.isBuilding.set(false);
                    this.participationHasLatestSubmissionWithoutResult.set(false);
                }
            });
        });
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
        const participationId = this.participation()?.id;
        const exerciseId = this.exercise().id;
        if (participationId === undefined || exerciseId === undefined) {
            return;
        }
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        this.submissionSubscription = this.submissionService
            .getLatestPendingSubmissionByParticipationId(participationId, exerciseId, this.personalParticipation)
            .pipe(
                tap(({ submissionState }) => {
                    switch (submissionState) {
                        case ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION:
                            this.isBuilding.set(false);
                            this.participationHasLatestSubmissionWithoutResult.set(false);
                            break;
                        case ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION:
                            this.isBuilding.set(true);
                            break;
                        case ProgrammingSubmissionState.HAS_FAILED_SUBMISSION:
                            this.participationHasLatestSubmissionWithoutResult.set(true);
                            this.isBuilding.set(false);
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
        const participationId = this.participation()?.id;
        if (participationId === undefined) {
            return;
        }
        if (this.resultSubscription) {
            this.resultSubscription.unsubscribe();
        }
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(participationId, this.personalParticipation, this.exercise().id)
            .pipe(
                filter((result) => !!result),
                tap((result: Result) => {
                    this.lastResultIsManual.set(!!result && isManualResult(result));
                }),
            )
            .subscribe();
    }

    abstract triggerBuild(event: MouseEvent): void;

    triggerWithType(submissionType: SubmissionType) {
        this.isRetrievingBuildStatus.set(true);
        const participationId = this.participation()?.id;
        if (participationId === undefined) {
            this.isRetrievingBuildStatus.set(false);
            return EMPTY;
        }
        return this.submissionService.triggerBuild(participationId, submissionType).pipe(finalize(() => this.isRetrievingBuildStatus.set(false)));
    }

    triggerFailed(lastGraded = false) {
        this.isRetrievingBuildStatus.set(true);
        const participationId = this.participation()?.id;
        if (participationId === undefined) {
            this.isRetrievingBuildStatus.set(false);
            return EMPTY;
        }
        return this.submissionService.triggerFailedBuild(participationId, lastGraded).pipe(finalize(() => this.isRetrievingBuildStatus.set(false)));
    }
}
