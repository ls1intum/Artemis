import { Component, Input, OnInit } from '@angular/core';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { LongFeedbackService } from 'app/exercises/shared/feedback/long-feedback.service';

@Component({
    selector: 'jhi-feedback-text',
    styleUrls: ['./feedback-text.scss'],
    templateUrl: './feedback-text.component.html',
})
export class FeedbackTextComponent implements OnInit {
    readonly MAX_DISPLAYABLE_LENGTH = 20_000;

    @Input() feedback: FeedbackItem;

    text = '';

    constructor(private longFeedbackService: LongFeedbackService) {}

    ngOnInit(): void {
        console.log(this.feedback.feedback);
        this.text = this.feedback.text ?? '';

        if (this.feedback.feedback.hasLongFeedback) {
            this.longFeedbackService.find(this.feedback.feedback.resultId, this.feedback.feedback.feedbackId).subscribe((longFeedbackResponse) => {
                const longFeedback = longFeedbackResponse.body!;
                console.log(longFeedback);
                this.text = longFeedback.text!;
            });
        }
    }
}
