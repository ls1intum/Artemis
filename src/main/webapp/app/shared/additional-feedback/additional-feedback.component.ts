import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-additional-feedback',
    templateUrl: './additional-feedback.component.html',
    styleUrls: ['./additional-feedback.component.scss'],
})
export class AdditionalFeedbackComponent {
    @Input()
    additionalFeedback: Feedback[];
    @Input()
    additional: boolean;
}
