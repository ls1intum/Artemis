import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { TumAetUiEmptyStateComponent, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { QuizQuestionPoolItemComponent } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool-item/quiz-question-pool-item.component';
import {
    PoolFilterVerdict,
    PoolQuestion,
    PoolQuestionView,
    PoolReviewRequest,
    PoolReviewState,
    PoolVerdictView,
} from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

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
    imports: [TumAetUiEmptyStateComponent, TumAetUiMessageComponent, TranslateDirective, ArtemisTranslatePipe, QuizQuestionPoolItemComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizQuestionPoolItemListComponent {
    readonly questions = input.required<PoolQuestion[]>();
    readonly canReview = input(false);
    readonly busyQuestionIds = input<ReadonlySet<number>>(new Set());
    /** Shows each question's competency and lecture unit, for lists that mix several units. */
    readonly showContext = input(false);
    /** Shows a delete button on each question. */
    readonly canDelete = input(false);
    /** Disables the delete buttons and explains why, while a build runs. */
    readonly deletionBlocked = input(false);
    readonly review = output<PoolReviewRequest>();
    readonly delete = output<number>();

    readonly expandedIds = signal<ReadonlySet<number>>(new Set());

    readonly judged = computed(() => this.questions().filter((question) => question.state === 'FILTERED' && !!question.question && !!question.decision));
    readonly needsReview = computed(() => this.judged().filter((question) => question.decision!.accepted && question.flagged === true));
    readonly accepted = computed(() => this.judged().filter((question) => question.decision!.accepted && question.flagged !== true));
    readonly rejected = computed(() => this.judged().filter((question) => !question.decision!.accepted));
    readonly inProgressCount = computed(() => this.questions().filter((question) => IN_PROGRESS_STATES.has(question.state)).length);
    readonly failedCount = computed(() => this.questions().filter((question) => question.state === 'FAILED_GENERATION' || question.state === 'FAILED_FILTER').length);
    private readonly displayIndexById = computed(() => new Map([...this.needsReview(), ...this.accepted(), ...this.rejected()].map((question, index) => [question.id, index])));
    readonly needsReviewViews = computed(() => this.needsReview().map((question) => this.toView(question)));
    readonly acceptedViews = computed(() => this.accepted().map((question) => this.toView(question)));
    readonly rejectedViews = computed(() => this.rejected().map((question) => this.toView(question)));

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

    private toView(question: PoolQuestion): PoolQuestionView {
        const verdicts = question.decision!.verdicts;
        return {
            question,
            index: this.displayIndexById().get(question.id) ?? 0,
            flaggedVerdicts: verdicts
                .filter((verdict) => verdict.flagged)
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
}
