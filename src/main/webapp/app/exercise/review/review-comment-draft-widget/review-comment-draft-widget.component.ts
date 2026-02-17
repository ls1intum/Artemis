import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, effect, inject, input, output } from '@angular/core';
import { ReviewCommentFacade } from 'app/exercise/review/review-comment-facade.service';
import { ReviewCommentDraftLocation } from 'app/exercise/review/review-comment.store';
import { FormsModule } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    private readonly reviewCommentFacade = inject(ReviewCommentFacade);

    readonly location = input<ReviewCommentDraftLocation>();
    readonly canSubmit = input<boolean>(true);
    readonly text = computed(() => {
        const location = this.location();
        return location ? this.reviewCommentFacade.getDraftText(location) : '';
    });
    readonly isSubmitting = computed(() => {
        const location = this.location();
        return location ? this.reviewCommentFacade.isDraftSubmitting(location) : false;
    });
    readonly onCancel = output<void>();

    constructor() {
        effect(() => {
            const location = this.location();
            if (location) {
                this.reviewCommentFacade.ensureDraft(location);
            }
        });
    }

    /**
     * Submits a new comment thread from the current draft location.
     */
    submitDraft(): void {
        const location = this.location();
        if (!location) {
            return;
        }
        if (!this.canSubmit() || this.reviewCommentFacade.isDraftSubmitting(location)) {
            return;
        }
        const text = this.reviewCommentFacade.getDraftText(location).trim();
        if (!text) {
            return;
        }
        this.reviewCommentFacade.submitCreateThread({
            targetType: location.targetType,
            initialLineNumber: location.lineNumber,
            initialFilePath: location.filePath,
            auxiliaryRepositoryId: location.auxiliaryRepositoryId,
            initialComment: { contentType: 'USER', text },
        });
    }

    /**
     * Stores draft text changes in the review-comment store.
     *
     * @param text The updated draft text.
     */
    onDraftTextChanged(text: string): void {
        const location = this.location();
        if (location) {
            this.reviewCommentFacade.setDraftText(location, text);
        }
    }

    /**
     * Cancels the draft and notifies the parent component.
     */
    cancel(): void {
        const location = this.location();
        if (location) {
            this.reviewCommentFacade.removeDraft(location);
        }
        this.onCancel.emit();
    }
}
