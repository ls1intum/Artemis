import { Component, input, model, output } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    template: '<div (drop)="updateFeedbackOnDrop($event)"></div>',
})
export class UnreferencedFeedbackDetailStubComponent {
    public readonly feedback = model.required<Feedback>();
    readonly resultId = input.required<number>();
    readonly isSuggestion = input<boolean>();
    public readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input<boolean>(false);
    readonly useDefaultFeedbackSuggestionBadgeText = input.required<boolean>();

    public readonly onFeedbackChange = output<Feedback>();
    public readonly onFeedbackDelete = output<Feedback>();
    readonly onAcceptSuggestion = output<Feedback>();
    readonly onDiscardSuggestion = output<Feedback>();

    updateFeedbackOnDrop(event: Event) {
        // stop the event-bubbling, just like in the actual component
        event.stopPropagation();
    }
}
