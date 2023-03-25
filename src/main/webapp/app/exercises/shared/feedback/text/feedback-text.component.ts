import { Component, Input, OnInit } from '@angular/core';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { LongFeedbackTextService } from 'app/exercises/shared/feedback/long-feedback-text.service';

@Component({
    selector: 'jhi-feedback-text',
    styleUrls: ['./feedback-text.scss'],
    templateUrl: './feedback-text.component.html',
})
export class FeedbackTextComponent implements OnInit {
    @Input() feedback: FeedbackItem;

    text?: string;

    constructor(private longFeedbackService: LongFeedbackTextService) {}

    ngOnInit(): void {
        this.text = this.feedback.text ?? '';

        if (this.feedback.feedbackReference.hasLongFeedback) {
            this.loadLongFeedback();
        }
    }

    private loadLongFeedback() {
        const resultId = this.feedback.feedbackReference.resultId;
        const feedbackId = this.feedback.feedbackReference.feedbackId;

        this.longFeedbackService.find(resultId, feedbackId).subscribe((longFeedbackResponse) => {
            const longFeedback = longFeedbackResponse.body!;
            this.text = longFeedback.text!;
        });
    }
}
