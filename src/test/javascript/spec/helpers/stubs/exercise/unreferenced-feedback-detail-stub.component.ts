import { Component, EventEmitter, Input, InputSignal, Output, input } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    template: '<div (drop)="updateFeedbackOnDrop($event)"></div>',
})
export class UnreferencedFeedbackDetailStubComponent {
    @Input() public feedback: Feedback;
    resultId: InputSignal<number> = input.required<number>();
    @Input() isSuggestion: boolean;
    @Input() public readOnly: boolean;
    @Input() highlightDifferences: boolean;
    @Input() useDefaultFeedbackSuggestionBadgeText: boolean;

    @Output() public onFeedbackChange = new EventEmitter<Feedback>();
    @Output() public onFeedbackDelete = new EventEmitter<Feedback>();
    @Output() onAcceptSuggestion = new EventEmitter<Feedback>();
    @Output() onDiscardSuggestion = new EventEmitter<Feedback>();

    updateFeedbackOnDrop(event: Event) {
        // stop the event-bubbling, just like in the actual component
        event.stopPropagation();
    }
}
