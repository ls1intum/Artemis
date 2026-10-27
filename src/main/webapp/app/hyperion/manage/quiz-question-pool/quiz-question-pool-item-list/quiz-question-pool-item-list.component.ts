import { NgTemplateOutlet } from '@angular/common';
import { TumAetUiButtonDirective, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { Component, ViewEncapsulation, computed, input, output, signal } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { QuizAiGeneratedQuestionCardComponent } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generated-question-card/quiz-ai-generated-question-card.component';
import { PoolFilterVerdict, PoolQuestion, PoolReviewState, isFlagged } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

export interface PoolReviewRequest {
    questionId: number;
    reviewState: PoolReviewState;
}

/** One verdict as the template shows it: its label key and its severity as an integer percentage. */
export interface PoolVerdictView {
    verdict: PoolFilterVerdict;
    labelKey: string;
    percent: number;
}

/** One listed question with everything the template shows, computed once per change. */
export interface PoolQuestionView {
    question: PoolQuestion;
    /** Position across all three sections, starting at 0. */
    index: number;
    /** The verdicts worth a badge, worst first. */
    flaggedVerdicts: PoolVerdictView[];
    verdicts: PoolVerdictView[];
    scorePercent: number;
    expanded: boolean;
    busy: boolean;
    reviewSeverity: 'success' | 'secondary' | 'contrast';
    reviewLabelKey: string;
}

const IN_PROGRESS_STATES = new Set(['PENDING', 'GENERATING', 'GENERATED', 'FILTERING']);

/** Renders a value in [0, 1] as an integer percentage. */
function asPercent(value: number): number {
    return Math.round(value * 100);
}

function toVerdictView(verdict: PoolFilterVerdict): PoolVerdictView {
    return { verdict, labelKey: `artemisApp.hyperion.quizQuestionPool.list.failureModes.${verdict.mode}`, percent: asPercent(verdict.severity) };
}

function reviewStateSeverity(reviewState: PoolReviewState): 'success' | 'secondary' | 'contrast' {
    switch (reviewState) {
        case 'APPROVED':
            return 'success';
        case 'EXCLUDED':
            return 'contrast';
        default:
            return 'secondary';
    }
}

/**
 * Lists the judged questions of one part of the pool in three sections (needs review, accepted, rejected by filter) and lets a
 * reviewer approve or exclude each one. Questions that are not judged yet are only counted.
 */
@Component({
    selector: 'jhi-quiz-question-pool-item-list',
    templateUrl: './quiz-question-pool-item-list.component.html',
    styleUrl: './quiz-question-pool-item-list.component.scss',
    encapsulation: ViewEncapsulation.None,
    imports: [
        NgTemplateOutlet,
        TumAetUiButtonDirective,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        QuizAiGeneratedQuestionCardComponent,
    ],
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
    readonly needsReviewViews = computed(() => this.needsReview().map((question) => this.toView(question)));
    readonly acceptedViews = computed(() => this.accepted().map((question) => this.toView(question)));
    readonly rejectedViews = computed(() => this.rejected().map((question) => this.toView(question)));

    private toView(question: PoolQuestion): PoolQuestionView {
        const verdicts = question.decision!.verdicts;
        return {
            question,
            index: this.displayIndexById().get(question.id) ?? 0,
            flaggedVerdicts: verdicts
                .filter((verdict) => verdict.triggered || verdict.severity > 0)
                .sort((a, b) => b.severity - a.severity)
                .map(toVerdictView),
            verdicts: verdicts.map(toVerdictView),
            scorePercent: asPercent(question.decision!.aggregateScore),
            expanded: this.expandedIds().has(question.id),
            busy: this.busyQuestionIds().has(question.id),
            reviewSeverity: reviewStateSeverity(question.reviewState),
            reviewLabelKey: `artemisApp.hyperion.quizQuestionPool.list.reviewState.${question.reviewState}`,
        };
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

    /** Approving an approved question, or excluding an excluded one, returns it to "not reviewed". */
    setReview(question: PoolQuestion, reviewState: PoolReviewState): void {
        const next = question.reviewState === reviewState ? 'PENDING' : reviewState;
        this.review.emit({ questionId: question.id, reviewState: next });
    }
}
