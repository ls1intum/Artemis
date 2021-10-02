import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { roundScore } from '../util/utils';
import { getCourseFromExercise } from 'app/entities/exercise.model';

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
    readonly roundScore = roundScore;
    readonly getCourseFromExercise = getCourseFromExercise;
}
