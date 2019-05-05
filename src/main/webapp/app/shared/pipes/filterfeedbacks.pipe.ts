import { PipeTransform, Pipe } from '@angular/core';
import { Feedback } from 'app/entities/feedback';

@Pipe({ name: 'filterfeedbacks' })
export class FilterFeedbacksPipe implements PipeTransform {
    transform(feedbacks: Feedback[]): any {
        if (!feedbacks) {
            return feedbacks;
        }
        return feedbacks.filter(feedback => !(feedback.type === 'AUTOMATIC' && feedback.positive));
    }
}
