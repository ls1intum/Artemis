import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { TranslateService } from '@ngx-translate/core';
import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { Exercise } from 'app/entities/exercise.model';

export interface FeedbackItemService {
    /**
     * Creates a feedback item with a name, title and text for each feedback object.
     * @param feedbacks The list of feedback objects.
     * @param showTestDetails
     */
    create(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[];

    /**
     * Uses {@link FeedbackItemGroup} predicate shouldContain and adds all that fulfill this predicate to its group
     * @param feedbackItems to be added to groups
     * @param exercise containing information needed to configure the {@link FeedbackItemGroup}
     */
    group(feedbackItems: FeedbackItem[], exercise: Exercise): (FeedbackItem | FeedbackItemGroup)[];
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
            type: 'Feedback',
            name: this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text,
            text: feedback.detailText,
            positive: feedback.positive,
            credits: feedback.credits,
        }));
    }
}
