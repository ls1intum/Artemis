import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { TranslateService } from '@ngx-translate/core';
import { computeFeedbackPreviewText } from 'app/exercises/shared/feedback/feedback.util';
import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';
import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/feedback/item/feedback-item';

export interface FeedbackItemService {
    /**
     * Creates a feedback item with a category, title and text for each feedback object.
     * @param feedbacks The list of feedback objects.
     * @param showTestDetails
     */
    create(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[];

    /**
     * Uses {@link FeedbackItemGroup} predicate shouldContain and adds all that fulfill this predicate to its group
     * @param feedbackItems to be added to groups
     */
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
            positive: feedback.positive,
            credits: feedback.credits,
        }));
    }
}
