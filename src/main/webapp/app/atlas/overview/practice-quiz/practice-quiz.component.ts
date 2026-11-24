import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faHistory, faListCheck, faPlay, faRedo } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { parseJson } from 'app/foundation/util/json.util';
import { PracticeQuizService } from 'app/atlas/overview/practice-quiz/practice-quiz.service';
import {
    PracticeSessionDTO,
    PracticeSessionReviewDTO,
    PracticeSessionSummaryDTO,
    PracticeSubmissionDTO,
    SubmittedAnswerFromLiveClientDTO,
} from 'app/atlas/overview/practice-quiz/practice-quiz.model';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { toQuizQuestion } from 'app/quiz/shared/util/generated-quiz-question.util';
import { addTemporaryHighlightToQuestion } from 'app/quiz/shared/questions/quiz-stepwizard.util';

@Component({
    selector: 'jhi-practice-quiz',
    templateUrl: './practice-quiz.component.html',
    styleUrls: ['../../../quiz/overview/participation/quiz-participation.component.scss'],
    imports: [
        CommonModule,
        FaIconComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        TranslateDirective,
        DecimalPipe,
        TumAetUiButtonDirective,
        TumAetUiTooltipDirective,
        MultipleChoiceQuestionComponent,
    ],
})
export class PracticeQuizComponent implements OnInit, OnDestroy {
    // 1. All inject() calls strictly before any other class members
    private readonly route = inject(ActivatedRoute);
    private readonly practiceQuizService = inject(PracticeQuizService);
    private readonly alertService = inject(AlertService);

    // 2. Component inputs & signals
    courseId = input<number>();
    private readonly routeCourseId = signal<number | undefined>(undefined);
    readonly activeCourseId = computed(() => this.courseId() ?? this.routeCourseId());

    readonly state = signal<'OVERVIEW' | 'QUIZ' | 'REVIEW'>('OVERVIEW');
    readonly isLoading = signal<boolean>(false);
    readonly isSubmitting = signal<boolean>(false);
    readonly errorMessage = signal<string | null>(null);

    readonly currentSession = signal<PracticeSessionDTO | null>(null);
    readonly reviewSession = signal<PracticeSessionReviewDTO | null>(null);
    readonly pastSessions = signal<PracticeSessionSummaryDTO[]>([]);

    // Questions and selections for active QUIZ mode
    readonly activeQuestions = signal<MultipleChoiceQuestion[]>([]);
    readonly selectedAnswerOptions = signal<Record<number, AnswerOption[]>>({});

    // Questions, selections, and scores for REVIEW mode
    readonly reviewQuestions = signal<MultipleChoiceQuestion[]>([]);
    readonly reviewSelectedAnswerOptions = signal<Record<number, AnswerOption[]>>({});
    readonly questionScores = signal<Record<number, number>>({});

    // FontAwesome icons
    readonly faPlay = faPlay;
    readonly faCheck = faCheck;
    readonly faRedo = faRedo;
    readonly faListCheck = faListCheck;
    readonly faHistory = faHistory;

    private paramSub?: Subscription;

    ngOnInit(): void {
        this.paramSub = this.route.parent?.paramMap.subscribe((params) => {
            const idParam = params.get('courseId');
            if (idParam) {
                const id = parseInt(idParam, 10);
                this.routeCourseId.set(id);
                this.loadOverview();
            }
        });
    }

    ngOnDestroy(): void {
        this.paramSub?.unsubscribe();
    }

    /**
     * Loads past sessions and checks if an unfinished session is currently active.
     */
    loadOverview(): void {
        const cId = this.activeCourseId();
        if (!cId) {
            return;
        }

        this.isLoading.set(true);
        this.errorMessage.set(null);

        // Check for an active session in progress first
        this.practiceQuizService.getCurrentSession(cId).subscribe({
            next: (session) => {
                if (session && session.questions && session.questions.length > 0) {
                    this.setupQuizSession(session);
                    this.isLoading.set(false);
                } else {
                    this.loadPastSessions(cId);
                }
            },
            error: () => {
                this.loadPastSessions(cId);
            },
        });
    }

    private loadPastSessions(cId: number): void {
        this.practiceQuizService.getPastSessions(cId).subscribe({
            next: (sessions) => {
                this.pastSessions.set(sessions);
                this.isLoading.set(false);
            },
            error: (err) => {
                this.isLoading.set(false);
                this.errorMessage.set(err?.error?.message ?? 'Failed to load past sessions.');
            },
        });
    }

    /**
     * Starts a new practice session and enters the quiz runner.
     */
    startNewQuiz(): void {
        const cId = this.activeCourseId();
        if (!cId || this.isLoading()) {
            return;
        }

        this.isLoading.set(true);
        this.errorMessage.set(null);

        this.practiceQuizService.startSession(cId).subscribe({
            next: (session) => {
                this.setupQuizSession(session);
                this.isLoading.set(false);
            },
            error: (err) => {
                this.isLoading.set(false);
                const msg = err?.error?.message ?? 'No quiz questions available for practice in this course yet.';
                this.alertService.error(msg);
                this.errorMessage.set(msg);
            },
        });
    }

    setupQuizSession(session: PracticeSessionDTO): void {
        this.currentSession.set(session);
        const questions = (session.questions ?? []).map((q) => toQuizQuestion(q)).filter((q): q is MultipleChoiceQuestion => q instanceof MultipleChoiceQuestion);
        this.activeQuestions.set(questions);
        this.selectedAnswerOptions.set({});
        this.state.set('QUIZ');
    }

    /**
     * Updates selected options for a specific question when modified through MultipleChoiceQuestionComponent.
     */
    onSelectedAnswerOptionsChange(questionId: number, options: AnswerOption[]): void {
        const current = deepClone(this.selectedAnswerOptions());
        current[questionId] = options;
        this.selectedAnswerOptions.set(current);
    }

    /**
     * Checks if a question has any selected answer options.
     */
    isAnswered(questionId: number): boolean {
        const answers = this.state() === 'REVIEW' ? this.reviewSelectedAnswerOptions()[questionId] : this.selectedAnswerOptions()[questionId];
        return (answers?.length ?? 0) > 0;
    }

    /**
     * Generates a tooltip string showing points achieved for a question in review mode.
     */
    questionTooltip(questionId: number, maxPoints: number): string {
        const score = this.questionScores()[questionId] ?? 0;
        return `${score}/${maxPoints} P`;
    }

    /**
     * Scrolls smoothly to the requested question index and applies a brief highlight pulse.
     */
    navigateToQuestion(index: number): void {
        const question = this.state() === 'QUIZ' ? this.activeQuestions()[index] : this.reviewQuestions()[index];
        if (question) {
            addTemporaryHighlightToQuestion(question);
        }
        const el = document.getElementById(`question${index}`);
        el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    /**
     * Submits the answered quiz to the server.
     */
    submitQuiz(): void {
        const cId = this.activeCourseId();
        const session = this.currentSession();
        if (!cId || !session) {
            return;
        }

        this.isSubmitting.set(true);
        this.errorMessage.set(null);

        const answers: SubmittedAnswerFromLiveClientDTO[] = this.activeQuestions().map((q) => {
            const selected = this.selectedAnswerOptions()[q.id!] ?? [];
            return {
                type: 'multiple-choice',
                quizQuestion: { id: q.id! },
                selectedOptions: selected.map((opt) => ({ id: opt.id! })),
            };
        });

        const submission: PracticeSubmissionDTO = { answers };

        this.practiceQuizService.submitSession(cId, session.id, submission).subscribe({
            next: (review) => {
                this.setupReview(review);
                this.currentSession.set(null);
                this.activeQuestions.set([]);
                this.isSubmitting.set(false);
            },
            error: (err) => {
                this.isSubmitting.set(false);
                const msg = err?.error?.message ?? 'Failed to submit quiz.';
                this.alertService.error(msg);
                this.errorMessage.set(msg);
            },
        });
    }

    /**
     * Opens the review for a specific session.
     */
    openReview(sessionId: number): void {
        const cId = this.activeCourseId();
        if (!cId) {
            return;
        }

        this.isLoading.set(true);
        this.errorMessage.set(null);

        this.practiceQuizService.getSessionReview(cId, sessionId).subscribe({
            next: (review) => {
                this.setupReview(review);
                this.isLoading.set(false);
            },
            error: (err) => {
                this.isLoading.set(false);
                const msg = err?.error?.message ?? 'Failed to load session review.';
                this.alertService.error(msg);
            },
        });
    }

    setupReview(review: PracticeSessionReviewDTO): void {
        this.reviewSession.set(review);

        const questions: MultipleChoiceQuestion[] = [];
        const selections: Record<number, AnswerOption[]> = {};
        const scores: Record<number, number> = {};

        for (const item of review.questions ?? []) {
            const q = toQuizQuestion(item.question);
            if (!(q instanceof MultipleChoiceQuestion)) {
                continue;
            }
            const mcQuestion = q;
            questions.push(mcQuestion);
            if (mcQuestion.id !== undefined) {
                scores[mcQuestion.id] = item.score ?? 0;

                let selectedOptionIds: number[] = [];
                if (item.submittedAnswer) {
                    try {
                        const parsed = parseJson<{ selectedOptions?: { id: number }[] }>(item.submittedAnswer);
                        if (Array.isArray(parsed?.selectedOptions)) {
                            selectedOptionIds = parsed.selectedOptions.map((o) => o.id);
                        }
                    } catch {
                        selectedOptionIds = [];
                    }
                }

                const chosen = (mcQuestion.answerOptions ?? []).filter((opt) => opt.id !== undefined && selectedOptionIds.includes(opt.id));
                selections[mcQuestion.id] = chosen;
            }
        }

        this.reviewQuestions.set(questions);
        this.reviewSelectedAnswerOptions.set(selections);
        this.questionScores.set(scores);
        this.state.set('REVIEW');
    }

    /**
     * Returns back to the overview dashboard.
     */
    backToOverview(): void {
        this.state.set('OVERVIEW');
        this.reviewSession.set(null);
        this.currentSession.set(null);
        this.activeQuestions.set([]);
        this.reviewQuestions.set([]);
        const cId = this.activeCourseId();
        if (cId) {
            this.isLoading.set(true);
            this.loadPastSessions(cId);
        }
    }
}
