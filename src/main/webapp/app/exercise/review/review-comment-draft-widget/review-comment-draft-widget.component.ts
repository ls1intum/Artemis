import { ChangeDetectionStrategy, Component, ViewEncapsulation, input, output } from '@angular/core';
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
    readonly canSubmit = input<boolean>(true);
    readonly text = input<string>('');
    readonly isSubmitting = input<boolean>(false);
    readonly onSubmitDraft = output<void>();
    readonly onTextChange = output<string>();
    readonly onCancel = output<void>();

    /**
     * Emits a submit intent when submission is allowed.
     */
    submitDraft(): void {
        if (!this.canSubmit() || this.isSubmitting()) {
            return;
        }
        this.onSubmitDraft.emit();
    }

    /**
     * Emits draft text changes so state can be stored outside the component.
     *
     * @param text The updated draft text.
     */
    onDraftTextChanged(text: string): void {
        this.onTextChange.emit(text);
    }

    /**
     * Cancels the draft and notifies the parent component.
     */
    cancel(): void {
        this.onCancel.emit();
    }
}
