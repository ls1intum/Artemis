import { Component } from '@angular/core';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { UnifiedTextResultComponent } from 'app/text/overview/text-result/unified-text-result.component';
import { UnifiedFeedbackComponent } from './unified-feedback.component';

@Component({
    selector: 'jhi-text-exercise-integration-test',
    template: `
        <div class="integration-test">
            <h2>Text Exercise Integration Test</h2>

            <h3>Unified Text Result Component</h3>
            <jhi-unified-text-result [result]="mockResult" />

            <h3>Individual Unified Feedback Components</h3>
            @for (feedback of testFeedbacks; track feedback.id) {
                <jhi-unified-feedback
                    [feedbackContent]="feedback.detailText || ''"
                    [points]="feedback.credits || 0"
                    [icon]="(feedback.credits || 0) > 0 ? 'success' : (feedback.credits || 0) < 0 ? 'error' : 'retry'"
                    [title]="(feedback.credits || 0) > 0 ? 'Good job!' : (feedback.credits || 0) < 0 ? 'Incorrect' : 'Needs revision'"
                >
                </jhi-unified-feedback>
            }
        </div>
    `,
    styles: [
        `
            .integration-test {
                padding: 2rem;
                max-width: 800px;
                margin: 0 auto;
            }
            h2,
            h3 {
                margin-bottom: 1rem;
            }
        `,
    ],
    imports: [UnifiedTextResultComponent, UnifiedFeedbackComponent],
})
export class TextExerciseIntegrationTestComponent {
    testFeedbacks: Feedback[] = [
        {
            id: 1,
            detailText: 'This is a positive feedback example.',
            credits: 2,
            type: FeedbackType.MANUAL,
            reference: '0',
            text: 'File text at line 1',
            positive: true,
        },
        {
            id: 2,
            detailText: 'This is a neutral feedback example.',
            credits: 0,
            type: FeedbackType.MANUAL,
            reference: '1',
            text: 'File text at line 2',
            positive: false,
        },
        {
            id: 3,
            detailText: 'This is a negative feedback example.',
            credits: -1,
            type: FeedbackType.MANUAL,
            reference: '2',
            text: 'File text at line 3',
            positive: false,
        },
    ];

    mockResult = {
        id: 1,
        submission: {
            id: 1,
            text: 'This is a test submission with feedback. Each sentence will have different feedback.',
            submitted: true,
        },
        feedbacks: this.testFeedbacks,
    };
}
