import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

@Injectable({ providedIn: 'root' })
export class FeedbackService extends BaseApiHttpService {
    /**
     * Filters the feedback based on the filter input
     * Used e.g. when we want to show certain test cases viewed from the exercise description
     * @param feedbacks The full list of feedback
     * @param filter an array of test case ids that the feedback needs to contain in its testcase.id attribute.
     */
    public filterFeedback = (feedbacks: Feedback[], filter: number[]): Feedback[] => {
        if (!filter) {
            return [...feedbacks];
        }
        return feedbacks.filter((feedback) => feedback.testCase?.id && filter.includes(feedback.testCase.id));
    };

    public async getLongFeedbackText(resultId: number, feedbackId: number): Promise<string> {
        const url = `results/${resultId}/feedbacks/${feedbackId}/long-feedback`;
        return await this.get<string>(url, { responseType: 'text' });
    }
}
