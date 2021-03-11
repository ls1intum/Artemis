import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { round } from '../util/utils';

@Component({
    selector: 'jhi-additional-feedback',
    templateUrl: './additional-feedback.component.html',
    styleUrls: ['./additional-feedback.component.scss'],
})
export class AdditionalFeedbackComponent {
    @Input()
    feedback: Feedback[];
    @Input()
    additional: boolean;

    // Expose the function to the template
    readonly round = round;
}
