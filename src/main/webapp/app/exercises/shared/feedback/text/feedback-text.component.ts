import { Component, Input, OnInit, inject } from '@angular/core';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { LongFeedbackTextService } from 'app/exercises/shared/feedback/long-feedback-text.service';

@Component({
    selector: 'jhi-feedback-text',
    styleUrls: ['./feedback-text.scss'],
    templateUrl: './feedback-text.component.html',
})
export class FeedbackTextComponent implements OnInit {
    private longFeedbackService = inject(LongFeedbackTextService);

    private readonly MAX_DISPLAYABLE_LENGTH = 20_000;

    @Input() feedback: FeedbackItem;

    text?: string;

    downloadText?: string;
    downloadFilename?: string;

    ngOnInit(): void {
        this.text = this.feedback.text ?? '';

        if (this.feedback.feedbackReference.hasLongFeedbackText) {
            this.loadLongFeedback();
        }
    }

    private loadLongFeedback() {
        const resultId = this.feedback.feedbackReference.result!.id!;
        const feedbackId = this.feedback.feedbackReference.id!;

        this.longFeedbackService.find(resultId, feedbackId).subscribe((longFeedbackResponse) => {
            const longFeedback = longFeedbackResponse.body!;
            const textLength = longFeedback.length ?? 0;

            if (textLength > this.MAX_DISPLAYABLE_LENGTH) {
                this.setDownloadInfo(longFeedback);
            } else {
                this.text = longFeedback;
            }
        });
    }

    private setDownloadInfo(longFeedback: string) {
        this.downloadText = 'data:text/plain;charset=utf-8,' + encodeURIComponent(longFeedback);
        this.downloadFilename = `feedback_${this.feedback.feedbackReference.id}.txt`;
    }
}
