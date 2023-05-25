import { Injectable } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Injectable({ providedIn: 'root' })
export class FeedbackService {
    /**
     * Filters the feedback based on the filter input
     * Used e.g. when we want to show certain test cases viewed from the exercise description
     * @param feedbacks The full list of feedback
     * @param filter an array of strings that the feedback needs to contain in its text attribute.
     */
    public filterFeedback = (feedbacks: Feedback[], filter: string[]): Feedback[] => {
        if (!filter) {
            return [...feedbacks];
        }
        return feedbacks.filter(({ text }) => text && filter.includes(text));
    };
}
