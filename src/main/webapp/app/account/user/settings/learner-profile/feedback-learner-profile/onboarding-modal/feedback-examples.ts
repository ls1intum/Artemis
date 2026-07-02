import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';

// Submissions are typed as TextSubmission so their `text` field is valid; assigning these typed values to
// Result.submission (of type Submission) is sound because TextSubmission extends Submission.
const briefSubmission: TextSubmission = { text: 'brief' };
const detailedSubmission: TextSubmission = { text: 'detailed' };
const formalSubmission: TextSubmission = { text: '- developers find a better solution' };
const friendlySubmission: TextSubmission = { text: '- developers find a better solution' };

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
            submission: briefSubmission,
        },
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
            submission: detailedSubmission,
        },
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
            submission: formalSubmission,
        },
        // Friendly-toned Feedback
        {
            feedbacks: [
                {
                    credits: 1,
                    detailText: 'You are on the right path! Try to reason your example in more detail, and clearly 😉',
                    reference: 'a better solution',
                },
            ],
            submission: friendlySubmission,
        },
    ],
};
