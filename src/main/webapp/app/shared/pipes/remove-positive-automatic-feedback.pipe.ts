import { Pipe, PipeTransform } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Pipe({ name: 'removepositiveautomaticfeedback' })
export class RemovePositiveAutomaticFeedbackPipe implements PipeTransform {
    /**
     * Automatic feedback that is positive gets removed as it will only contain the test name, but not any useful feedback for the student.
     * Manual feedback gets not filtered as some feedback might be provided even if the feedback is positive.
     * @param feedbacks The array of Feedback items.
     */
    transform(feedbacks: Feedback[]): any {
        return feedbacks.filter((feedback) => !(feedback.type === 'AUTOMATIC' && feedback.positive));
    }
}
