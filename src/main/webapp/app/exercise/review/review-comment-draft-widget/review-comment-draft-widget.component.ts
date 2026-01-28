import { ChangeDetectionStrategy, Component, ViewEncapsulation, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-review-comment-draft-widget',
    templateUrl: './review-comment-draft-widget.component.html',
    styleUrls: ['./review-comment-draft-widget.component.scss'],
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

    submit(): void {
        if (!this.canSubmit()) {
            return;
        }
        const trimmed = this.text.trim();
        if (!trimmed) {
            return;
        }
        this.onSubmit.emit(trimmed);
    }

    cancel(): void {
        this.onCancel.emit();
    }
}
