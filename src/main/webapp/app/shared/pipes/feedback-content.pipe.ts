import { Pipe, PipeTransform } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';

@Pipe({
    name: 'feedbackContent',
    // impure: we need to detect changes in attributes of the feedback
    pure: false,
})
export class FeedbackContentPipe implements PipeTransform {
    /**
     * Extracts the content from a feedback.
     *
     * Can be either the detail text if present, or the grading instruction text otherwise.
     *
     * @param feedback Some feedback.
     */
    transform(feedback: Feedback): string | undefined {
        if (feedback.detailText) {
            return feedback.detailText;
        } else {
            return feedback.gradingInstruction?.feedback;
        }
    }
}
