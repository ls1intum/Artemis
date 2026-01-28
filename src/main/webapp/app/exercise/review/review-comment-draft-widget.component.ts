import { ChangeDetectionStrategy, Component, ViewEncapsulation, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-review-comment-draft-widget',
    templateUrl: './review-comment-draft-widget.component.html',
    styleUrls: ['./review-comment-widget.shared.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ArtemisTranslatePipe],
})
export class ReviewCommentDraftWidgetComponent {
    readonly canSubmit = input<boolean>(true);
    readonly onSubmit = output<string>();
    readonly onCancel = output<void>();

    text = '';
    showSubmitError = false;

    submit(): void {
        if (!this.canSubmit()) {
            this.showSubmitError = true;
            return;
        }
        const trimmed = this.text.trim();
        if (!trimmed) {
            return;
        }
        this.showSubmitError = false;
        this.onSubmit.emit(trimmed);
    }

    cancel(): void {
        this.showSubmitError = false;
        this.onCancel.emit();
    }
}
