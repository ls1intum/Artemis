import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, input } from '@angular/core';
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

    athenaEnabled = false;
    isRequestingFeedback = false;
    feedbackRequested = false;
    hasUserAcceptedLLMUsage = false;

    athenaFeedbackUsed = 0;
    athenaFeedbackLimit = 0;

    readonly isVisible = computed(() => {
        const exam = this.studentExam();
        return !!exam?.exam?.testExam && this.athenaEnabled && !!exam.submitted && !this.testExamConduction();
    });

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
        this.athenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);
        this.feedbackRequested = this.localStorageService.retrieve<boolean>(this.getFeedbackRequestedStorageKey()) ?? false;
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
        this.hasUserAcceptedLLMUsage = selection === LLMSelectionDecision.CLOUD_AI;
    }

    async requestAIFeedback(): Promise<void> {
        if (!this.hasUserAcceptedLLMUsage) {
            await this.showLLMSelectionModal();
            return;
        }
        this.triggerFeedbackRequest();
    }

    private async showLLMSelectionModal(): Promise<void> {
        const choice = await this.llmModalService.open(this.accountService.userIdentity()?.selectedLLMUsage);
        if (choice === LLMSelectionDecision.CLOUD_AI) {
            this.llmSelectionSubscription = this.userService.updateLLMSelectionDecision(LLMSelectionDecision.CLOUD_AI).subscribe(() => {
                this.hasUserAcceptedLLMUsage = true;
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
        this.isRequestingFeedback = true;
        this.feedbackSubscription = this.examParticipationService.requestAthenaFeedback(ids.courseId, ids.examId, ids.studentExamId).subscribe({
            next: () => {
                this.feedbackRequested = true;
                this.isRequestingFeedback = false;
                this.localStorageService.store(this.getFeedbackRequestedStorageKey(), true);
                this.alertService.success('artemisApp.exam.examSummary.feedbackRequestSent');
            },
            error: (error: HttpErrorResponse) => {
                this.isRequestingFeedback = false;
                this.alertService.error(`artemisApp.exercise.${error.error?.errorKey}`);
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
                this.athenaFeedbackUsed = usage.used;
                this.athenaFeedbackLimit = usage.limit;
                // If the server already counts this attempt as consumed, don't bump again on incoming websocket results.
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
        for (const exercise of this.studentExam()?.exercises ?? []) {
            if (exercise.type !== ExerciseType.TEXT && exercise.type !== ExerciseType.MODELING) {
                continue;
            }
            const participationId = exercise.studentParticipations?.[0]?.id;
            if (!participationId) {
                continue;
            }
            const subscription = this.participationWebsocketService
                .subscribeForLatestResultOfParticipation(participationId, true)
                .pipe(
                    skip(1),
                    filter((result): result is Result => !!result),
                    filter((result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA),
                )
                .subscribe((result) => this.handleAthenaResult(result));
            this.athenaResultSubscriptions.push(subscription);
        }
    }

    private handleAthenaResult(result: Result): void {
        if (!result.completionDate || !result.successful || this.currentAttemptCounted) {
            return;
        }
        this.athenaFeedbackUsed += 1;
        this.currentAttemptCounted = true;
    }

    private getFeedbackRequestedStorageKey(): string {
        return `${FEEDBACK_REQUESTED_LOCAL_STORAGE_PREFIX}${this.studentExam()?.id}`;
    }
}
