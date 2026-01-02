import { ChangeDetectionStrategy, Component, ViewEncapsulation, effect, input, output } from '@angular/core';
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
    readonly onSubmit = output<string>();
    readonly onCancel = output<void>();

    text = '';
    readonly initialText = input<string>('');

    constructor() {
        effect(() => {
            this.text = this.initialText();
        });
    }

    submit(): void {
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
