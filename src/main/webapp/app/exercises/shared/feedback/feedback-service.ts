import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { Result } from 'app/entities/result.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class FeedbackService {
    constructor(private resultService: ResultService) {}

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

    /**
     * Loads the missing feedback details
     * @param participationId the current participation
     * @param result
     */
    public getFeedbacksForResult(participationId: number, result: Result): Observable<Feedback[]> {
        return this.resultService.getFeedbackDetailsForResult(participationId, result).pipe(map(({ body: feedbackList }) => feedbackList!));
    }
}
