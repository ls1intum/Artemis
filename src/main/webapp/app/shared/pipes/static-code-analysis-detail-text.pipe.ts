import { Pipe, PipeTransform } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Pipe({
    name: 'staticCodeAnalysisDetailText',
})
export class StaticCodeAnalysisDetailTextPipe implements PipeTransform {
    /**
     * Creates the result text from a feedback instance.
     * The reference field of Feedback is used to store the file and the line.
     * @param {Feedback} feedback - Feedback instance for which detail text is created
     * @returns {string} Static Assessment detail text
     */
    transform(feedback: Feedback): string {
        const refArr = feedback.reference!.split(':');
        return `In file ${refArr[0]} at line ${refArr[1]}:\n ${feedback.detailText}`;
    }
}
