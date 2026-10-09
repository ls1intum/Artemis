import { Component, input } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { faCommentDots } from '@fortawesome/free-regular-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';

@Component({
    selector: 'jhi-additional-feedback',
    templateUrl: './additional-feedback.component.html',
    imports: [FaIconComponent, TranslateDirective, UnifiedFeedbackComponent],
})
export class AdditionalFeedbackComponent {
    readonly feedback = input<Feedback[]>(undefined!);
    readonly additional = input<boolean>(undefined!);
    readonly course = input<Course>();

    // Icons
    faCommentDots = faCommentDots;

    // Expose the function to the template
    readonly getCourseFromExercise = getCourseFromExercise;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;
}
