import { Component } from '@angular/core';
import { FeedbackType, UnifiedFeedbackComponent } from './unified-feedback.component';

@Component({
    selector: 'jhi-unified-feedback-demo',
    templateUrl: './unified-feedback-demo.component.html',
    styleUrls: ['./unified-feedback-demo.component.scss'],
    imports: [UnifiedFeedbackComponent],
})
export class UnifiedFeedbackDemoComponent {
    // Demo data for different feedback variations
    demoFeedbacks = [
        {
            title: 'Explicit Correct Type',
            feedbackContent:
                'Strategy Comparison: You attempted correctly to list down different methods. Next up: Consider how factors like system latency would differ with different strategies.',
            points: 2,
            type: 'correct' as FeedbackType,
            reference: undefined,
        },
        {
            title: 'Explicit Needs Revision Type',
            feedbackContent:
                'Rate Limiter Explanation: Your explanation of rate limiting is on the right track, but it could be deeper. Try to elaborate on why rate limiting is crucial in distributed systems, focusing on aspects like fairness, system stability, and preventing overload. Next up: Look at real-world examples.',
            points: 1,
            type: 'needs_revision' as FeedbackType,
            reference: 'One method is token bucket, which lets requests to go through if there are tokens. Tokens refill over time.',
        },
        {
            title: 'Explicit Not Attempted Type',
            feedbackContent:
                "Strategy Comparison: You didn't attempt to compare different strategies. Next up: Consider how factors like system latency would differ with different strategies.",
            points: 0,
            type: 'not_attempted' as FeedbackType,
            reference: 'src/prg/BubbleSort.java:11-14',
        },
        {
            title: undefined, // Will be auto-generated
            feedbackContent: 'Good job! Your implementation is correct and follows best practices.',
            points: 5,
            type: undefined, // Will be inferred as 'correct' based on points
            reference: undefined,
        },
        {
            title: undefined, // Will be auto-generated
            feedbackContent:
                'Your code demonstrates good understanding of the algorithm, but there are several areas for improvement. The time complexity could be optimized, and the error handling needs to be more robust.',
            points: 0,
            type: undefined, // Will be inferred as 'not_attempted' based on points
            reference: 'src/algorithms/sorting/QuickSort.java:45-67',
        },
        {
            title: 'Custom Title with Auto Type',
            feedbackContent: 'Excellent work on the implementation! All test cases pass and the code is well-structured.',
            points: 10,
            type: undefined, // Will be inferred as 'correct' based on points
            reference: undefined,
        },
    ];
}
