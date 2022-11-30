import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/result/result-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { computeFeedbackPreviewText } from 'app/exercises/shared/feedback/feedback.util';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { map } from 'rxjs/operators';

export interface FeedbackService {
    /**
     * Creates a feedback item with a category, title and text for each feedback object.
     * @param feedbacks The list of feedback objects.
     * @param showTestDetails
     */
    createFeedbackItems(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[];

    /**
     * Filters / Summarizes positive test cases for a student and programming exercise result
     * @param feedbackItems The list of feedback items
     * @param showTestDetails
     */
    filterFeedbackItems(feedbackItems: FeedbackItem[], showTestDetails: boolean): FeedbackItem[];

    /**
     * Gets positive test cases without detail texts
     * @param feedbackItems
     */
    getPositiveTestCasesWithoutDetailText(feedbackItems: FeedbackItem[]): FeedbackItem[];
}

@Injectable({ providedIn: 'root' })
export class FeedbackServiceImpl implements FeedbackService {
    constructor(private translateService: TranslateService, private resultService: ResultService) {}

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    public createFeedbackItems(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[] {
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

    filterFeedbackItems(feedbackItems: FeedbackItem[]): FeedbackItem[] {
        return [...feedbackItems];
    }

    /**
     * Filters the feedback based on the filter input
     * @param feedbacks The full list of feedback
     * @param filter an array of strings that the feedback needs to contain in its text attribute.
     */
    public filterFeedback = (feedbacks: Feedback[], filter: string[]): Feedback[] => {
        if (!filter) {
            return [...feedbacks];
        }
        return feedbacks.filter(({ text }) => text && filter.includes(text));
    };

    /**
     * Loads the missing feedback details
     * @param participationId the current participation
     * @param resultId the current result
     */
    public getDetailsForResult(participationId: number, resultId: number) {
        return this.resultService.getFeedbackDetailsForResult(participationId, resultId).pipe(map(({ body: feedbackList }) => feedbackList!));
    }
}
