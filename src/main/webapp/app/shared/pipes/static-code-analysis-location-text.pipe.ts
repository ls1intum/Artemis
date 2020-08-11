import { Pipe, PipeTransform } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';

@Pipe({
    name: 'staticCodeAnalysisLocationText',
})
export class StaticCodeAnalysisLocationTextPipe implements PipeTransform {
    /**
     * Creates the location text from a static code analysis feedback instance.
     * The reference field of Feedback is used to store the file and the line.
     * @param {Feedback} feedback - Feedback instance for which the location text is created
     * @returns {string} Static Code analysis issue location
     */
    transform(feedback: Feedback): string {
        const refArr = feedback.reference!.split(':');
        return `${refArr[0]} at line ${refArr[1]}`;
    }
}
