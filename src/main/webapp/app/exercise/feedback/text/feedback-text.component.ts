import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit, inject } from '@angular/core';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { LongFeedbackTextService } from 'app/exercise/feedback/services/long-feedback-text.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-feedback-text',
    styleUrls: ['./feedback-text.scss'],
    templateUrl: './feedback-text.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective],
})
export class FeedbackTextComponent implements OnInit {
    private longFeedbackService = inject(LongFeedbackTextService);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);

    private readonly MAX_DISPLAYABLE_LENGTH = 20_000;

    @Input() feedback: FeedbackItem;

    text?: string;

    downloadText?: string;
    downloadFilename?: string;

    ngOnInit(): void {
        this.text = this.feedback.text ?? '';

        if (this.feedback.feedbackReference.hasLongFeedbackText) {
            this.loadLongFeedback();
        } else {
            this.changeDetectorRef.markForCheck();
        }
    }

    private loadLongFeedback() {
        if (this.feedback.feedbackReference.id) {
            const feedbackId = this.feedback.feedbackReference.id;

            this.longFeedbackService.find(feedbackId).subscribe((longFeedbackResponse) => {
                const longFeedback = longFeedbackResponse.body!;
                const textLength = longFeedback.length ?? 0;

                if (textLength > this.MAX_DISPLAYABLE_LENGTH) {
                    this.setDownloadInfo(longFeedback);
                } else {
                    this.text = longFeedback;
                }
                this.changeDetectorRef.markForCheck();
            });
        }
    }

    private setDownloadInfo(longFeedback: string) {
        this.downloadText = 'data:text/plain;charset=utf-8,' + encodeURIComponent(longFeedback);
        this.downloadFilename = `feedback_${this.feedback.feedbackReference.id}.txt`;
    }
}
