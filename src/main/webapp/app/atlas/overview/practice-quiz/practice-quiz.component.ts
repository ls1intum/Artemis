import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faAward, faCheck, faCheckCircle, faClock, faHistory, faLightbulb, faPlay, faRedo, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { PracticeQuizService } from 'app/atlas/overview/practice-quiz/practice-quiz.service';
import {
    PracticeSessionDTO,
    PracticeSessionReviewDTO,
    PracticeSessionSummaryDTO,
    PracticeSubmissionDTO,
    SubmittedAnswerFromLiveClientDTO,
} from 'app/atlas/overview/practice-quiz/practice-quiz.model';

@Component({
    selector: 'jhi-practice-quiz',
    templateUrl: './practice-quiz.component.html',
    styleUrls: ['./practice-quiz.component.scss'],
    imports: [CommonModule, FaIconComponent, ArtemisDatePipe, DecimalPipe],
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

    // Maps questionId -> array of selected option IDs
    readonly selectedOptions = signal<Record<number, number[]>>({});

    // FontAwesome icons
    readonly faPlay = faPlay;
    readonly faCheck = faCheck;
    readonly faTimes = faTimes;
    readonly faCheckCircle = faCheckCircle;
    readonly faTimesCircle = faTimesCircle;
    readonly faArrowLeft = faArrowLeft;
    readonly faRedo = faRedo;
    readonly faLightbulb = faLightbulb;
    readonly faHistory = faHistory;
    readonly faAward = faAward;
    readonly faClock = faClock;

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
                    this.currentSession.set(session);
                    this.state.set('QUIZ');
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
        if (!cId) {
            return;
        }

        this.isLoading.set(true);
        this.errorMessage.set(null);

        this.practiceQuizService.startSession(cId).subscribe({
            next: (session) => {
                this.currentSession.set(session);
                this.selectedOptions.set({});
                this.state.set('QUIZ');
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

    /**
     * Toggles an option for multiple-choice questions.
     */
    toggleOption(questionId: number, optionId: number, singleChoice?: boolean): void {
        const current = deepClone(this.selectedOptions());
        const selected = current[questionId] ?? [];

        if (singleChoice) {
            current[questionId] = selected.includes(optionId) ? [] : [optionId];
        } else {
            if (selected.includes(optionId)) {
                current[questionId] = selected.filter((id) => id !== optionId);
            } else {
                current[questionId] = [...selected, optionId];
            }
        }
        this.selectedOptions.set(current);
    }

    isOptionSelected(questionId: number, optionId: number): boolean {
        return (this.selectedOptions()[questionId] ?? []).includes(optionId);
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

        const answers: SubmittedAnswerFromLiveClientDTO[] = session.questions.map((q) => {
            const optionIds = this.selectedOptions()[q.id] ?? [];
            return {
                type: 'multiple-choice',
                quizQuestion: { id: q.id },
                selectedOptions: optionIds.map((optId) => ({ id: optId })),
            };
        });

        const submission: PracticeSubmissionDTO = { answers };

        this.practiceQuizService.submitSession(cId, session.id, submission).subscribe({
            next: (review) => {
                this.reviewSession.set(review);
                this.currentSession.set(null);
                this.state.set('REVIEW');
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
                this.reviewSession.set(review);
                this.state.set('REVIEW');
                this.isLoading.set(false);
            },
            error: (err) => {
                this.isLoading.set(false);
                const msg = err?.error?.message ?? 'Failed to load session review.';
                this.alertService.error(msg);
            },
        });
    }

    /**
     * Returns back to the overview dashboard.
     */
    backToOverview(): void {
        this.state.set('OVERVIEW');
        this.reviewSession.set(null);
        this.currentSession.set(null);
        this.loadOverview();
    }
}
