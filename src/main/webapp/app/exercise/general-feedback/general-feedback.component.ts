import { Component, input } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';

/**
 * Shows a student the feedback of an assessment that does not refer to a part of the submission, under a
 * "General feedback" heading with its count. The same section is used by every exercise type, so general feedback
 * looks alike whether it accompanies a diagram, a text, code or an uploaded file. Renders nothing without feedback.
 */
@Component({
    selector: 'jhi-general-feedback',
    templateUrl: './general-feedback.component.html',
    styleUrl: './general-feedback.component.scss',
    imports: [TranslateDirective, UnifiedFeedbackComponent],
})
export class GeneralFeedbackComponent {
    readonly feedbacks = input<Feedback[] | undefined>([]);
    readonly course = input<Course>();

    protected readonly buildFeedbackTextForReview = buildFeedbackTextForReview;
}
