import { Component, Input } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/entities/feedback.model';
import { roundScoreSpecifiedByCourseSettings } from '../util/utils';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';

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
    @Input()
    course?: Course;

    // Expose the function to the template
    readonly roundScoreSpecifiedByCourseSettings = roundScoreSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;

    public buildFeedbackTextForReview(feedback: Feedback): string {
        return buildFeedbackTextForReview(feedback);
    }
}
