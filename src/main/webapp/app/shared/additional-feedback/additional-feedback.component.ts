import { Component, Input } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/entities/feedback.model';
import { roundValueSpecifiedByCourseSettings } from '../util/utils';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { faCommentDots } from '@fortawesome/free-regular-svg-icons';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

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

    // Icons
    farCommentDots = faCommentDots;
    faExclamationTriangle = faExclamationTriangle;

    // Expose the function to the template
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;
}
