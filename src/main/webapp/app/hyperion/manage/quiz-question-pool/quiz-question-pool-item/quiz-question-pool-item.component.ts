import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { QuizAiGeneratedQuestionCardComponent } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generated-question-card/quiz-ai-generated-question-card.component';
import { PoolQuestionView, PoolReviewRequest, PoolReviewState } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

/**
 * One judged pool question: the question card, the filter's flagged verdicts, the expandable rationale and the review decision.
 */
@Component({
    selector: 'jhi-quiz-question-pool-item',
    templateUrl: './quiz-question-pool-item.component.html',
    imports: [
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        QuizAiGeneratedQuestionCardComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizQuestionPoolItemComponent {
    readonly item = input.required<PoolQuestionView>();
    readonly canReview = input(false);
    /** Shows the question's competency and lecture unit above the card. */
    readonly showContext = input(false);
    /** Shows the delete button; deleting is an instructor's action. */
    readonly canDelete = input(false);
    /** Disables the delete button and explains why, while a build runs. */
    readonly deletionBlocked = input(false);
    readonly toggleRationale = output<number>();
    readonly review = output<PoolReviewRequest>();
    readonly delete = output<number>();

    readonly faTriangleExclamation = faTriangleExclamation;

    /** Approving an approved question, or excluding an excluded one, returns it to "not reviewed". */
    setReview(reviewState: PoolReviewState): void {
        const question = this.item().question;
        this.review.emit({ questionId: question.id, reviewState: question.reviewState === reviewState ? 'PENDING' : reviewState });
    }
}
