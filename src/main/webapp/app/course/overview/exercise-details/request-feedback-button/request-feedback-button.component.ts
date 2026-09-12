import { Component, OnDestroy, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Subscription, filter, skip } from 'rxjs';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faPenSquare } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';

import { isExamExercise } from 'app/foundation/util/utils';
import { ExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/account/user/shared/user.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { isAthenaAIResult } from 'app/exercise/result/result.utils';
import dayjs from 'dayjs/esm';

// Mirrors the server-side default for `artemis.athena.allowed-feedback-requests`
export const DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT = 10;

export function countSuccessfulAthenaFeedbackRequests(participation?: StudentParticipation): number {
    return (
        getAllResultsOfAllSubmissions(participation?.submissions)?.filter((result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA && result.successful === true)
            .length ?? 0
    );
}

function isPendingAthenaFeedbackResult(result: Result | undefined): boolean {
    return !!result && isAthenaAIResult(result) && result.successful === undefined && (!result.completionDate || dayjs().isSameOrBefore(result.completionDate));
}

@Component({
    selector: 'jhi-request-feedback-button',
    imports: [FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective],
    templateUrl: './request-feedback-button.component.html',
})
export class RequestFeedbackButtonComponent implements OnInit, OnDestroy {
    private readonly profileService = inject(ProfileService);
    private readonly alertService = inject(AlertService);
    private readonly courseExerciseService = inject(CourseExerciseService);
    private readonly translateService = inject(TranslateService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly participationService = inject(ParticipationService);
    private readonly accountService = inject(AccountService);
    private readonly userService = inject(UserService);
    private readonly participationWebsocketService = inject(ParticipationWebsocketService);
    private readonly llmModalService = inject(LLMSelectionModalService);

    protected readonly faPenSquare = faPenSquare;

    protected readonly ExerciseType = ExerciseType;

    readonly athenaEnabled = signal(false);
    readonly requestFeedbackEnabled = signal(false);
    readonly isExamExercise = signal<boolean>(undefined!);
    participation?: StudentParticipation;
    readonly hasUserAcceptedLLMUsage = signal(false);
    currentFeedbackRequestCount = signal(0);
    readonly feedbackRequestLimit = DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT;
    readonly isFeedbackLimitReached = computed(() => this.currentFeedbackRequestCount() >= this.feedbackRequestLimit);
    private readonly isFeedbackRequestPending = signal(false);

    isSubmitted = input<boolean>();
    pendingChanges = input<boolean>(false);
    hasAthenaResultForLatestSubmission = input<boolean>(false);
    readonly isFeedbackGenerationInProgress = this.isFeedbackRequestPending.asReadonly();
    smallButtons = input<boolean>(false);
    exercise = input.required<Exercise>();
    readonly participationId = input<number>();

    private athenaResultUpdateListener?: Subscription;
    private acceptSubscription?: Subscription;
    private exerciseDetailsSubscription?: Subscription;
    private feedbackRequestTimeout?: ReturnType<typeof setTimeout>;
    private hasLoadedParticipationContext = false;
    private loadedExerciseId?: number;
    private loadedParticipationId?: number;

    constructor() {
        effect(() => {
            const exerciseId = this.exercise().id;
            const participationId = this.participationId();
            untracked(() => {
                if (
                    this.hasLoadedParticipationContext &&
                    (exerciseId !== this.loadedExerciseId || participationId !== this.loadedParticipationId) &&
                    exerciseId &&
                    !isExamExercise(this.exercise())
                ) {
                    this.updateParticipation();
                }
            });
        });
    }

    private isAcceptedLLMSelection(selection?: LLMSelectionDecision): boolean {
        return selection === LLMSelectionDecision.CLOUD_AI || selection === LLMSelectionDecision.LOCAL_AI;
    }

    ngOnInit() {
        this.athenaEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA));
        this.isExamExercise.set(isExamExercise(this.exercise()));
        if (this.isExamExercise() || !this.exercise().id) {
            return;
        }
        this.requestFeedbackEnabled.set(this.exercise().course?.athenaFormativeFeedbackEnabled ?? false);
        this.updateParticipation();
        this.setUserAcceptedLLMUsage();
    }
    ngOnDestroy(): void {
        this.athenaResultUpdateListener?.unsubscribe();
        this.acceptSubscription?.unsubscribe();
        this.exerciseDetailsSubscription?.unsubscribe();
        clearTimeout(this.feedbackRequestTimeout);
    }

    private updateParticipation() {
        const exerciseId = this.exercise().id;
        if (exerciseId) {
            const participationId = this.participationId();
            this.loadedExerciseId = exerciseId;
            this.loadedParticipationId = participationId;
            this.hasLoadedParticipationContext = true;
            this.exerciseDetailsSubscription?.unsubscribe();
            this.athenaResultUpdateListener?.unsubscribe();
            this.athenaResultUpdateListener = undefined;
            this.participation = undefined;
            this.currentFeedbackRequestCount.set(0);
            this.syncFeedbackRequestPendingState(undefined);

            this.exerciseDetailsSubscription = this.exerciseService.getExerciseDetails(exerciseId).subscribe({
                next: (exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                    const participations = exerciseResponse.body!.exercise.studentParticipations ?? [];
                    this.participation = this.selectParticipation(participations, participationId);
                    if (this.participation) {
                        this.currentFeedbackRequestCount.set(countSuccessfulAthenaFeedbackRequests(this.participation));
                        const pendingAthenaResult = getAllResultsOfAllSubmissions(this.participation.submissions).find(isPendingAthenaFeedbackResult);
                        this.syncFeedbackRequestPendingState(pendingAthenaResult);
                        this.subscribeToResultUpdates();
                    }
                },
                error: (error: HttpErrorResponse) => {
                    this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            });
        }
    }

    private selectParticipation(participations: StudentParticipation[], participationId: number | undefined): StudentParticipation | undefined {
        if (participationId !== undefined) {
            return participations.find((participation) => participation.id === participationId);
        }
        const practiceParticipation = this.participationService.getSpecificStudentParticipation(participations, true);
        const gradedParticipation = this.participationService.getSpecificStudentParticipation(participations, false);
        return practiceParticipation ?? gradedParticipation;
    }

    setUserAcceptedLLMUsage(): void {
        const selection = this.accountService.userIdentity()?.selectedLLMUsage;
        this.hasUserAcceptedLLMUsage.set(this.isAcceptedLLMSelection(selection));
    }

    async showLLMSelectionModal(): Promise<void> {
        const choice = await this.llmModalService.open(this.accountService.userIdentity()?.selectedLLMUsage);

        switch (choice) {
            case LLMSelectionDecision.CLOUD_AI:
                this.acceptLLMUsage(LLMSelectionDecision.CLOUD_AI);
                break;
            case LLMSelectionDecision.LOCAL_AI:
                this.acceptLLMUsage(LLMSelectionDecision.LOCAL_AI);
                break;
            case LLMSelectionDecision.NO_AI:
                // Store that the user actively declined AI usage
                this.acceptLLMUsage(LLMSelectionDecision.NO_AI);
                break;
            case LLM_MODAL_DISMISSED:
                break;
        }
    }

    acceptLLMUsage(decision: LLMSelectionDecision) {
        this.acceptSubscription?.unsubscribe();

        this.acceptSubscription = this.userService.updateLLMSelectionDecision(decision).subscribe(() => {
            const hasAccepted = this.isAcceptedLLMSelection(decision);

            this.hasUserAcceptedLLMUsage.set(hasAccepted);
            this.accountService.setUserLLMSelectionDecision(decision);

            // Proceed with feedback request only when an AI option was accepted
            if (hasAccepted && this.assureConditionsSatisfied()) {
                this.processFeedbackRequest();
            }
        });
    }

    async requestAIFeedback(): Promise<void> {
        if (this.isFeedbackLimitReached()) {
            return;
        }
        if (!this.hasUserAcceptedLLMUsage()) {
            await this.showLLMSelectionModal();
            return;
        }
        this.requestFeedback();
    }

    private subscribeToResultUpdates() {
        if (!this.participation?.id) {
            return;
        }

        // Subscribe to result updates for this participation
        this.athenaResultUpdateListener?.unsubscribe();
        this.athenaResultUpdateListener = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id, true)
            .pipe(
                skip(1), // Skip initial value
                filter((result): result is Result => !!result),
                filter((result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA),
            )
            .subscribe(this.handleAthenaAssessment.bind(this));
    }

    private handleAthenaAssessment(result: Result) {
        if (result.completionDate && result.successful) {
            this.currentFeedbackRequestCount.update((count) => count + 1);
        }
        this.syncFeedbackRequestPendingState(result);
    }

    private syncFeedbackRequestPendingState(result: Result | undefined): void {
        clearTimeout(this.feedbackRequestTimeout);
        const isPending = isPendingAthenaFeedbackResult(result);
        this.isFeedbackRequestPending.set(isPending);
        if (isPending && result?.completionDate) {
            const timeout = Math.max(0, dayjs(result.completionDate).diff(dayjs(), 'milliseconds'));
            this.feedbackRequestTimeout = setTimeout(() => this.isFeedbackRequestPending.set(false), timeout);
        }
    }

    requestFeedback() {
        const participationId = this.participationId();
        this.exerciseService.getExerciseDetails(this.exercise().id!).subscribe({
            next: (exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                const participations = exerciseResponse.body!.exercise.studentParticipations ?? [];
                const participation = this.selectParticipation(participations, participationId);
                if (!this.assureConditionsSatisfied(participation)) {
                    return;
                }
                this.processFeedbackRequest(participation);
            },
            error: (error: HttpErrorResponse) => {
                this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
            },
        });
    }

    private processFeedbackRequest(participation = this.participation) {
        this.courseExerciseService.requestFeedback(this.exercise().id!, participation!.id!).subscribe({
            next: (updatedParticipation: StudentParticipation) => {
                if (updatedParticipation) {
                    if (this.participationId() === undefined || this.participationId() === participation?.id) {
                        this.isFeedbackRequestPending.set(true);
                    }
                    this.alertService.success('artemisApp.exercise.feedbackRequestSent');
                }
            },
            error: (error: HttpErrorResponse) => {
                this.alertService.error(`artemisApp.exercise.${error.error.errorKey}`);
            },
        });
    }

    /**
     * Checks if the conditions for requesting automatic non-graded feedback are satisfied.
     * The student can request automatic non-graded feedback under the following conditions:
     * 1. They have a participation with a submission.
     * 2. There is no already pending feedback request.
     * @returns {boolean} `true` if all conditions are satisfied, otherwise `false`.
     */
    assureConditionsSatisfied(participation = this.participation): boolean {
        if (!participation?.id) {
            return false;
        }
        if (this.exercise().type === ExerciseType.PROGRAMMING) {
            // Athena feedback requests for programming exercises require manual assessment to be enabled
            return this.exercise().assessmentType === AssessmentType.SEMI_AUTOMATIC;
        }
        return this.assureTextModelingConditions();
    }

    /**
     * Special conditions for text exercises.
     * Not more than 1 request per submission.
     * No request with pending changes (these would be overwritten after participation update)
     */
    assureTextModelingConditions(): boolean {
        if (this.hasAthenaResultForLatestSubmission()) {
            const submitFirstWarning = this.translateService.instant('artemisApp.exercise.submissionAlreadyHasAthenaResult');
            this.alertService.warning(submitFirstWarning);
            return false;
        }
        if (this.pendingChanges()) {
            const pendingChangesMessage = this.translateService.instant('artemisApp.exercise.feedbackRequestPendingChanges');
            this.alertService.warning(pendingChangesMessage);
            return false;
        }
        return true;
    }
}
