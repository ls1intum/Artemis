import { Result } from 'app/exercise/shared/entities/result/result.model';

export const FEEDBACK_EXAMPLES: { [step: number]: [Result, Result] } = {
    0: [
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
    1: [
        // Formal-toned Feedback
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText:
                        'Expand on the reasons why changes are frequent in software development, such as market demands, technological evolution, and internal organizational shifts.',
                    reference: 'a better solution',
                },
            ],
            submission: { text: '- developers find a better solution' },
        } as Result,
        // Friendly-toned Feedback
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText: 'You are on the right path! Try to reason your example in more detail, and clearly 😉',
                    reference: 'a better solution',
                },
            ],
            submission: { text: '- developers find a better solution' },
        } as Result,
    ],
};
