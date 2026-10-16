import { Component, input, model, output } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    template: '<div (drop)="updateFeedbackOnDrop($event)"></div>',
})
export class UnreferencedFeedbackDetailStubComponent {
    public readonly feedback = model.required<Feedback>();
    readonly resultId = input.required<number>();
    public readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input<boolean>(false);

    public readonly onFeedbackChange = output<Feedback>();
    public readonly onFeedbackDelete = output<Feedback>();

    updateFeedbackOnDrop(event: Event) {
        // stop the event-bubbling, just like in the actual component
        event.stopPropagation();
    }
}
