import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { LongFeedbackTextApi } from 'app/openapi/api/long-feedback-text-api';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-feedback-text',
    styleUrls: ['./feedback-text.css'],
    templateUrl: './feedback-text.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective],
})
export class FeedbackTextComponent implements OnInit {
    private longFeedbackApi = inject(LongFeedbackTextApi);

    feedback = input.required<FeedbackItem>();

    readonly text = signal<string | undefined>(undefined);

    readonly downloadText = signal<string | undefined>(undefined);
    readonly downloadFilename = signal<string | undefined>(undefined);

    ngOnInit(): void {
        this.text.set(this.feedback().text ?? '');

        if (this.feedback().feedbackReference.hasLongFeedbackText) {
            this.loadLongFeedback();
        }
    }

    private loadLongFeedback() {
        const feedbackId = this.feedback().feedbackReference.id;
        if (feedbackId) {
            this.longFeedbackApi.getLongFeedback(feedbackId).subscribe((longFeedback) => {
                this.text.set(longFeedback);
                this.setDownloadInfo(longFeedback);
            });
        }
    }

    private setDownloadInfo(longFeedback: string) {
        this.downloadText.set('data:text/plain;charset=utf-8,' + encodeURIComponent(longFeedback));
        this.downloadFilename.set(`feedback_${this.feedback().feedbackReference.id}.txt`);
    }
}
