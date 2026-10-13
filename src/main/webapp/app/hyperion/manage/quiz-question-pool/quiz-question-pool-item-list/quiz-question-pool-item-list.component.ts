import { NgTemplateOutlet } from '@angular/common';
import { Component, ViewEncapsulation, computed, input, output, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { QuizAiGeneratedQuestionCardComponent } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generated-question-card/quiz-ai-generated-question-card.component';
import { PoolFailureMode, PoolFilterDecision, PoolFilterVerdict, PoolQuestion, PoolReviewState, isFlagged } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

export interface PoolReviewRequest {
    questionId: number;
    reviewState: PoolReviewState;
}

const IN_PROGRESS_STATES = new Set(['PENDING', 'GENERATING', 'GENERATED', 'FILTERING']);

/**
 * Lists the judged questions of one part of the pool in three sections (needs review, accepted, rejected by filter) and lets a
 * reviewer approve or exclude each one. Questions that are not judged yet are only counted.
 */
@Component({
    selector: 'jhi-quiz-question-pool-item-list',
    templateUrl: './quiz-question-pool-item-list.component.html',
    styleUrl: './quiz-question-pool-item-list.component.scss',
    encapsulation: ViewEncapsulation.None,
    imports: [NgTemplateOutlet, ButtonModule, TagModule, TooltipModule, TranslateDirective, ArtemisTranslatePipe, QuizAiGeneratedQuestionCardComponent],
})
export class QuizQuestionPoolItemListComponent {
    questions = input.required<PoolQuestion[]>();
    canReview = input(false);
    busyQuestionIds = input<ReadonlySet<number>>(new Set());
    review = output<PoolReviewRequest>();

    readonly expandedIds = signal<ReadonlySet<number>>(new Set());

    readonly judged = computed(() => this.questions().filter((question) => question.state === 'FILTERED' && !!question.question && !!question.decision));
    readonly needsReview = computed(() => this.judged().filter((question) => question.decision!.accepted && isFlagged(question.decision!)));
    readonly accepted = computed(() => this.judged().filter((question) => question.decision!.accepted && !isFlagged(question.decision!)));
    readonly rejected = computed(() => this.judged().filter((question) => !question.decision!.accepted));
    readonly inProgressCount = computed(() => this.questions().filter((question) => IN_PROGRESS_STATES.has(question.state)).length);
    readonly failedCount = computed(() => this.questions().filter((question) => question.state === 'FAILED_GENERATION' || question.state === 'FAILED_FILTER').length);
    private readonly displayIndexById = computed(() => new Map([...this.needsReview(), ...this.accepted(), ...this.rejected()].map((question, index) => [question.id, index])));

    displayIndex(id: number): number {
        return this.displayIndexById().get(id) ?? 0;
    }

    isExpanded(id: number): boolean {
        return this.expandedIds().has(id);
    }

    toggleRationale(id: number): void {
        this.expandedIds.update((ids) => {
            const next = new Set(ids);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    }

    isBusy(id: number): boolean {
        return this.busyQuestionIds().has(id);
    }

    /** The verdicts worth a badge, worst first. */
    flaggedVerdicts(decision: PoolFilterDecision): PoolFilterVerdict[] {
        return decision.verdicts.filter((verdict) => verdict.triggered || verdict.severity > 0).sort((a, b) => b.severity - a.severity);
    }

    getFailureModeLabelKey(mode: PoolFailureMode): string {
        return `artemisApp.hyperion.quizQuestionPool.list.failureModes.${mode}`;
    }

    getReviewStateLabelKey(reviewState: PoolReviewState): string {
        return `artemisApp.hyperion.quizQuestionPool.list.reviewState.${reviewState}`;
    }

    getReviewStateSeverity(reviewState: PoolReviewState): 'success' | 'secondary' | 'contrast' {
        switch (reviewState) {
            case 'APPROVED':
                return 'success';
            case 'EXCLUDED':
                return 'contrast';
            default:
                return 'secondary';
        }
    }

    /** Renders a value in [0, 1] as an integer percentage. */
    asPercent(value: number): number {
        return Math.round(value * 100);
    }

    /** Approving an approved question, or excluding an excluded one, returns it to "not reviewed". */
    setReview(question: PoolQuestion, reviewState: PoolReviewState): void {
        const next = question.reviewState === reviewState ? 'PENDING' : reviewState;
        this.review.emit({ questionId: question.id, reviewState: next });
    }
}
