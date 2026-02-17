import { ChangeDetectionStrategy, Component, ViewEncapsulation, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CommentThreadLocationType, CreateCommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';

@Component({
    selector: 'jhi-review-comment-draft-widget',
    templateUrl: './review-comment-draft-widget.component.html',
    styleUrls: ['./review-comment-draft-widget.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ButtonDirective, ArtemisTranslatePipe],
})
export class ReviewCommentDraftWidgetComponent {
    readonly canSubmit = input<boolean>(true);
    readonly targetType = input<CommentThreadLocationType | undefined>(undefined);
    readonly lineNumber = input<number | undefined>(undefined);
    readonly filePath = input<string | undefined>(undefined);
    readonly auxiliaryRepositoryId = input<number | undefined>(undefined);

    readonly onSubmitted = output<void>();
    readonly onCancel = output<void>();

    text = '';
    private readonly reviewCommentService = inject(ExerciseReviewCommentService);

    /**
     * Creates a review thread in the current exercise context when submission is allowed and non-empty.
     */
    submit(): void {
        if (!this.canSubmit()) {
            return;
        }
        const trimmed = this.text.trim();
        const targetType = this.targetType();
        const lineNumber = this.lineNumber();
        if (!trimmed || !targetType || !lineNumber) {
            return;
        }
        const payload: CreateCommentThread = {
            targetType,
            initialLineNumber: lineNumber,
            initialComment: { contentType: 'USER', text: trimmed },
        };
        if (targetType !== CommentThreadLocationType.PROBLEM_STATEMENT && this.filePath()) {
            payload.initialFilePath = this.filePath();
        }
        if (this.auxiliaryRepositoryId() !== undefined) {
            payload.auxiliaryRepositoryId = this.auxiliaryRepositoryId();
        }
        this.reviewCommentService.createThreadInContext(payload, () => this.onSubmitted.emit());
    }

    /**
     * Cancels the draft and notifies the parent component.
     */
    cancel(): void {
        this.onCancel.emit();
    }
}
