import { Component } from '@angular/core';
import { FeedbackItem } from 'app/exercises/shared/result/result-detail.component';
import { Feedback } from 'app/entities/feedback.model';

type FeedbackItemGroup =
    | 'missing' // Test case with no weight
    | 'wrong' // Test cases subtracting from score
    | 'warning' // Static code analysis
    | 'info' // Tutor feedback with no weight
    | 'correct'; // Positive test cases

type FeedbackItemMap = {
    [key in FeedbackItemGroup]: FeedbackItem[];
};

@Component({
    selector: 'jhi-feedback-list-programming',
    templateUrl: './feedback-list-programming.component.html',
})
export class FeedbackListProgrammingComponent {
    feedbackMap: FeedbackItemMap;

    private createFeedbackItemsMap(feedbacks: Feedback[]): FeedbackItemMap {
        const result: FeedbackItemMap = { correct: [], info: [], missing: [], warning: [], wrong: [] };

        feedbacks.forEach((feedback) => {
            if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
                console.log('lol');
            }
        });

        return result;
    }
}
