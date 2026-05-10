import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { Subscription, filter, skip } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faRobot, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { AccountService } from 'app/core/auth/account.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { UserService } from 'app/core/user/shared/user.service';
import { PROFILE_ATHENA } from 'app/app.constants';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

const FEEDBACK_REQUESTED_LOCAL_STORAGE_PREFIX = 'artemis_exam_ai_feedback_requested_';

@Component({
    selector: 'jhi-exam-request-ai-feedback-button',
    templateUrl: './exam-request-ai-feedback-button.component.html',
    imports: [FaIconComponent, TranslateDirective],
})
export class ExamRequestAiFeedbackButtonComponent implements OnInit, OnDestroy {
    private readonly examParticipationService = inject(ExamParticipationService);
    private readonly profileService = inject(ProfileService);
    private readonly accountService = inject(AccountService);
    private readonly userService = inject(UserService);
    private readonly localStorageService = inject(LocalStorageService);
    private readonly llmModalService = inject(LLMSelectionModalService);
    private readonly participationWebsocketService = inject(ParticipationWebsocketService);
    private readonly alertService = inject(AlertService);

    readonly courseId = input.required<number>();
    readonly studentExam = input.required<StudentExam>();
    readonly testExamConduction = input<boolean>(false);

    protected readonly faRobot = faRobot;
    protected readonly faSpinner = faSpinner;

    readonly athenaEnabled = signal(false);
    readonly isRequestingFeedback = signal(false);
    readonly feedbackRequested = signal(false);
    readonly hasUserAcceptedLLMUsage = signal(false);

    readonly athenaFeedbackUsed = signal(0);
    readonly athenaFeedbackLimit = signal(0);

    readonly receivedAthenaResultExerciseIds = signal<ReadonlySet<number>>(new Set());

    readonly hasExerciseWithFeedbackSuggestionModule = computed(() => {
        return (this.studentExam()?.exercises ?? []).some(
            (exercise) => (exercise.type === ExerciseType.TEXT || exercise.type === ExerciseType.MODELING) && !!exercise.feedbackSuggestionModule,
        );
    });

    readonly isVisible = computed(() => {
        const exam = this.studentExam();
        return !!exam?.exam?.testExam && this.athenaEnabled() && !!exam.submitted && !this.testExamConduction() && this.hasExerciseWithFeedbackSuggestionModule();
    });

    private readonly eligibleExerciseIds = computed(() => {
        return (this.studentExam()?.exercises ?? [])
            .filter((exercise) => (exercise.type === ExerciseType.TEXT || exercise.type === ExerciseType.MODELING) && !!exercise.feedbackSuggestionModule)
            .map((exercise) => exercise.id)
            .filter((id): id is number => id !== undefined);
    });

    readonly hasAllAthenaResultsForCurrentAttempt = computed(() => {
        const eligible = this.eligibleExerciseIds();
        if (eligible.length === 0) {
            return false;
        }
        const received = this.receivedAthenaResultExerciseIds();
        return eligible.every((id) => received.has(id));
    });

    // Spins until every eligible exercise has produced an Athena result for the current attempt.
    // Per-exercise alerts may fire earlier as individual results arrive.
    readonly isGenerating = computed(() => (this.isRequestingFeedback() || this.feedbackRequested()) && !this.hasAllAthenaResultsForCurrentAttempt());

    get hasAnyAthenaResultForCurrentAttempt(): boolean {
        return (this.studentExam()?.exercises ?? []).some((exercise) => {
            if (exercise.type !== ExerciseType.TEXT && exercise.type !== ExerciseType.MODELING) {
                return false;
            }
            const latestResult = getLatestResultOfStudentParticipation(exercise.studentParticipations?.[0], false);
            return latestResult?.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
        });
    }

    private feedbackSubscription?: Subscription;
    private llmSelectionSubscription?: Subscription;
    private athenaResultSubscriptions: Subscription[] = [];
    private currentAttemptCounted = false;

    ngOnInit(): void {
        this.athenaEnabled.set(this.profileService.isProfileActive(PROFILE_ATHENA));
        this.feedbackRequested.set(this.localStorageService.retrieve<boolean>(this.getFeedbackRequestedStorageKey()) ?? false);
        this.setUserAcceptedLLMUsage();
        this.loadAthenaFeedbackUsage();
    }

    ngOnDestroy(): void {
        this.feedbackSubscription?.unsubscribe();
        this.llmSelectionSubscription?.unsubscribe();
        this.athenaResultSubscriptions.forEach((subscription) => subscription.unsubscribe());
    }

    private setUserAcceptedLLMUsage(): void {
        const selection = this.accountService.userIdentity()?.selectedLLMUsage;
        this.hasUserAcceptedLLMUsage.set(selection === LLMSelectionDecision.CLOUD_AI);
    }

    async requestAIFeedback(): Promise<void> {
        if (!this.hasUserAcceptedLLMUsage()) {
            await this.showLLMSelectionModal();
            return;
        }
        this.triggerFeedbackRequest();
    }

    private async showLLMSelectionModal(): Promise<void> {
        const choice = await this.llmModalService.open(this.accountService.userIdentity()?.selectedLLMUsage);
        if (choice === LLMSelectionDecision.CLOUD_AI) {
            this.llmSelectionSubscription = this.userService.updateLLMSelectionDecision(LLMSelectionDecision.CLOUD_AI).subscribe(() => {
                this.hasUserAcceptedLLMUsage.set(true);
                this.accountService.setUserLLMSelectionDecision(LLMSelectionDecision.CLOUD_AI);
                this.triggerFeedbackRequest();
            });
        } else if (choice !== LLM_MODAL_DISMISSED) {
            this.llmSelectionSubscription = this.userService.updateLLMSelectionDecision(choice as LLMSelectionDecision).subscribe(() => {
                this.accountService.setUserLLMSelectionDecision(choice as LLMSelectionDecision);
            });
        }
    }

    private triggerFeedbackRequest(): void {
        const ids = this.getExamRequestIds();
        if (!ids) {
            return;
        }
        this.isRequestingFeedback.set(true);
        this.feedbackSubscription = this.examParticipationService.requestAthenaFeedback(ids.courseId, ids.examId, ids.studentExamId).subscribe({
            next: () => {
                this.feedbackRequested.set(true);
                this.isRequestingFeedback.set(false);
                this.localStorageService.store(this.getFeedbackRequestedStorageKey(), true);
                this.alertService.success('artemisApp.exam.examSummary.feedbackRequestSent');
            },
            error: (error: HttpErrorResponse) => {
                this.isRequestingFeedback.set(false);
                const errorKey = error.error?.errorKey;
                this.alertService.error(errorKey ? `artemisApp.exercise.${errorKey}` : `error.http.${error.status}`);
            },
        });
    }

    private loadAthenaFeedbackUsage(): void {
        if (!this.isVisible()) {
            return;
        }
        const ids = this.getExamRequestIds();
        if (!ids) {
            return;
        }
        this.examParticipationService.getAthenaFeedbackUsage(ids.courseId, ids.examId, ids.studentExamId).subscribe({
            next: (usage) => {
                this.athenaFeedbackUsed.set(usage.used);
                this.athenaFeedbackLimit.set(usage.limit);
                // If the server already counts this attempt as consumed, don't bump again on incoming websocket results.
                this.currentAttemptCounted = this.hasAnyAthenaResultForCurrentAttempt;
                this.subscribeToAthenaResultsForCurrentAttempt();
            },
            error: () => {
                this.alertService.error('artemisApp.exam.examSummary.feedbackUsageLoadFailed');
                this.currentAttemptCounted = this.hasAnyAthenaResultForCurrentAttempt;
                this.subscribeToAthenaResultsForCurrentAttempt();
            },
        });
    }

    private getExamRequestIds(): { courseId: number; examId: number; studentExamId: number } | undefined {
        const exam = this.studentExam();
        const examId = exam?.exam?.id;
        const studentExamId = exam?.id;
        if (examId === undefined || studentExamId === undefined) {
            return undefined;
        }
        return { courseId: this.courseId(), examId, studentExamId };
    }

    private subscribeToAthenaResultsForCurrentAttempt(): void {
        this.athenaResultSubscriptions.forEach((subscription) => subscription.unsubscribe());
        this.athenaResultSubscriptions = [];

        const initialReceived = new Set<number>();
        for (const exercise of this.studentExam()?.exercises ?? []) {
            if ((exercise.type !== ExerciseType.TEXT && exercise.type !== ExerciseType.MODELING) || exercise.id === undefined) {
                continue;
            }
            const latestResult = getLatestResultOfStudentParticipation(exercise.studentParticipations?.[0], false);
            if (latestResult?.assessmentType === AssessmentType.AUTOMATIC_ATHENA) {
                initialReceived.add(exercise.id);
            }
        }
        this.receivedAthenaResultExerciseIds.set(initialReceived);

        for (const exercise of this.studentExam()?.exercises ?? []) {
            if (exercise.type !== ExerciseType.TEXT && exercise.type !== ExerciseType.MODELING) {
                continue;
            }
            const participationId = exercise.studentParticipations?.[0]?.id;
            const exerciseId = exercise.id;
            if (!participationId || exerciseId === undefined) {
                continue;
            }
            const subscription = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(participationId, true)
                .pipe(
                    skip(1),
                    filter((result): result is Result => !!result),
                    filter((result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA),
                )
                .subscribe((result) => this.handleAthenaResult(result, exerciseId));
            this.athenaResultSubscriptions.push(subscription);
        }
    }

    private handleAthenaResult(result: Result, exerciseId?: number): void {
        if (!result.completionDate || !result.successful) {
            return;
        }
        if (exerciseId !== undefined) {
            this.receivedAthenaResultExerciseIds.update((set) => {
                if (set.has(exerciseId)) {
                    return set;
                }
                const next = new Set(set);
                next.add(exerciseId);
                return next;
            });
        }
        if (this.currentAttemptCounted) {
            return;
        }
        this.athenaFeedbackUsed.update((used) => used + 1);
        this.currentAttemptCounted = true;
    }

    private getFeedbackRequestedStorageKey(): string {
        return `${FEEDBACK_REQUESTED_LOCAL_STORAGE_PREFIX}${this.studentExam()?.id}`;
    }
}
