import { Result } from 'app/exercise/shared/entities/result/result.model';

export const FEEDBACK_EXAMPLES: { [step: number]: [Result, Result] } = {
    0: [
        // Alternative Feedback
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText:
                        'Your explanation of what rate limiting is and why it is necessary in distributed systems is clear and concise. You effectively highlight the importance of protecting systems from overload and abuse, which is crucial in environments with high concurrent usage.',
                    reference: 'rate limiting',
                },
            ],
            submission: { text: 'rate limiting' },
        } as Result,
        // Standard Feedback
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText: 'Your explanation of what rate limiting is and why it is necessary in distributed systems is clear and concise.',
                    reference: 'rate limiting',
                },
            ],
            submission: { text: 'rate limiting' },
        } as Result,
    ],
    1: [
        // Follow-up Summary A
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText: 'You have shown a good understanding of the topic. For further improvement, consider exploring edge cases and potential pitfalls.',
                    reference: 'summary',
                },
            ],
            submission: { text: 'summary' },
        } as Result,
        // Follow-up Summary B
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText: 'You fully met the expectations for this part, great work!',
                    reference: 'summary',
                },
            ],
            submission: { text: 'summary' },
        } as Result,
    ],
    2: [
        // Brief Feedback
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText: 'Good job.',
                    reference: 'brief',
                },
            ],
            submission: { text: 'brief' },
        } as Result,
        // Detailed Feedback
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText:
                        'Your answer is well-structured and covers all relevant aspects. You provided clear examples and justified your reasoning, which demonstrates a deep understanding of the topic.',
                    reference: 'detailed',
                },
            ],
            submission: { text: 'detailed' },
        } as Result,
    ],
};
