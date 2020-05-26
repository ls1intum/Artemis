import { Pipe, PipeTransform } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Pipe({
    name: 'staticAssessmentDetailText',
})
export class StaticAssessmentDetailTextPipe implements PipeTransform {
    /**
     * Creates the result text from a feedback instance.
     * Property reference is used to store the file and the line.
     * @param {Feedback} feedback - Feedback instance for which detail text is created
     * @returns {string} Static Assessment detail text
     */
    transform(feedback: Feedback): string {
        const refArr = feedback.reference!.split(':');
        if (refArr.length > 1) {
            return `In file ${refArr[0]} at line ${refArr[1]}:\n ${feedback.detailText}`;
        }
        return feedback.detailText!;
    }
}
