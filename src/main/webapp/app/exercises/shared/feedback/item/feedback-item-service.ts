import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/result/detail/result-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { computeFeedbackPreviewText } from 'app/exercises/shared/feedback/feedback.util';
import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';

export interface FeedbackItemService {
    /**
     * Creates a feedback item with a category, title and text for each feedback object.
     * @param feedbacks The list of feedback objects.
     * @param showTestDetails
     */
    create(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[];

    /**
     * @deprecated TODO: refactor by exposing summary() method
     * Gets positive test cases without detail texts
     * @param feedbackItems
     */
    getPositiveTestCasesWithoutDetailText(feedbackItems: FeedbackItem[]): FeedbackItem[];

    group(feedbackItems: FeedbackItem[]): (FeedbackItem | FeedbackItemGroup)[];
}

@Injectable({ providedIn: 'root' })
export class FeedbackItemServiceImpl implements FeedbackItemService {
    constructor(private translateService: TranslateService) {}

    group(feedbackItems: FeedbackItem[]): (FeedbackItem | FeedbackItemGroup)[] {
        return feedbackItems;
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    create(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[] {
        return feedbacks.map((feedback) => ({
            type: FeedbackItemType.Feedback,
            category: this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text,
            text: feedback.detailText,
            previewText: computeFeedbackPreviewText(feedback.detailText),
            positive: feedback.positive,
            credits: feedback.credits,
        }));
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    getPositiveTestCasesWithoutDetailText(feedbackItems: FeedbackItem[]): FeedbackItem[] {
        return [];
    }
}
