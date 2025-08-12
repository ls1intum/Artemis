import { Component } from '@angular/core';
import { UnifiedFeedbackComponent } from './unified-feedback.component';

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
            title: 'Success Example',
            feedbackContent:
                'Strategy Comparison: You attempted correctly to list down different methods. Next up: Consider how factors like system latency would differ with different strategies.',
            points: 2,
            icon: 'success' as const,
            reference: undefined,
        },
        {
            title: 'Error Example',
            feedbackContent:
                "Strategy Comparison: You didn't attempt to compare different strategies. Next up: Consider how factors like system latency would differ with different strategies.",
            points: 0,
            icon: 'error' as const,
            reference: undefined,
        },
        {
            title: 'Retry Example',
            feedbackContent:
                'Rate Limiter Explanation: Your explanation of rate limiting is on the right track, but it could be deeper. Try to elaborate on why rate limiting is crucial in distributed systems, focusing on aspects like fairness, system stability, and preventing overload. Next up: Look at real-world examples.',
            points: 2,
            icon: 'retry' as const,
            reference: 'One method is token bucket, which lets requests to go through if there are tokens. Tokens refill over time.',
        },
        {
            title: 'Missing',
            feedbackContent:
                "Strategy Comparison: You didn't attempt to compare different strategies. Next up: Consider how factors like system latency would differ with different strategies.",
            points: 0,
            icon: 'error' as const,
            reference: 'src/prg/BubbleSort.java:11-14',
        },
        {
            title: undefined,
            feedbackContent: 'Good job! Your implementation is correct and follows best practices.',
            points: 5,
            icon: 'success' as const,
            reference: undefined,
        },
        {
            title: 'Complex Feedback',
            feedbackContent:
                'Your code demonstrates good understanding of the algorithm, but there are several areas for improvement. The time complexity could be optimized, and the error handling needs to be more robust. Consider implementing proper input validation and edge case handling.',
            points: 3,
            icon: 'retry' as const,
            reference: 'src/algorithms/sorting/QuickSort.java:45-67',
        },
    ];
}
