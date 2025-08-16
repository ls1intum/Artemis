import { Component } from '@angular/core';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { UnifiedFeedbackComponent } from './unified-feedback.component';
import { UnifiedTextResultComponent } from 'app/text/overview/text-result/unified-text-result.component';
import { UnifiedAdditionalFeedbackComponent } from 'app/exercise/additional-feedback/unified-additional-feedback.component';

@Component({
    selector: 'jhi-text-exercise-demo',
    templateUrl: './text-exercise-demo.component.html',
    styleUrls: ['./text-exercise-demo.component.scss'],
    imports: [UnifiedFeedbackComponent, UnifiedTextResultComponent, UnifiedAdditionalFeedbackComponent],
})
export class TextExerciseDemoComponent {
    // Sample feedback data for demonstration
    sampleFeedbacks: Feedback[] = [
        {
            id: 1,
            detailText: 'Your explanation of the concept is excellent and demonstrates deep understanding.',
            credits: 2,
            type: FeedbackType.MANUAL,
            reference: '0',
            text: 'File text at line 1',
            positive: true,
        },
        {
            id: 2,
            detailText: 'The argument could be strengthened with more specific examples.',
            credits: 0,
            type: FeedbackType.MANUAL,
            reference: '1',
            text: 'File text at line 2',
            positive: false,
        },
        {
            id: 3,
            detailText: 'Good start, but consider adding more detail to support your main points.',
            credits: 1,
            type: FeedbackType.MANUAL,
            reference: '2',
            text: 'File text at line 3',
            positive: true,
        },
    ];

    additionalFeedbacks: Feedback[] = [
        {
            id: 4,
            detailText: 'Overall, this is a well-structured response that addresses the key points.',
            credits: 3,
            type: FeedbackType.MANUAL,
            reference: undefined,
            text: 'General feedback',
            positive: true,
        },
        {
            id: 5,
            detailText: 'Consider improving your conclusion to better summarize your main arguments.',
            credits: 0,
            type: FeedbackType.MANUAL,
            reference: undefined,
            text: 'General feedback',
            positive: false,
        },
    ];

    // Mock result for text result component
    mockResult = {
        id: 1,
        submission: {
            id: 1,
            text: 'This is a sample text submission with multiple sentences. Each sentence demonstrates different aspects of writing. The feedback will be shown inline.',
            submitted: true,
        },
        feedbacks: this.sampleFeedbacks,
    };
}
