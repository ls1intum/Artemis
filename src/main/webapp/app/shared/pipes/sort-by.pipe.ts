import { Pipe, PipeTransform } from '@angular/core';
import { DifferencePipe } from 'ngx-moment';
import { Submission } from 'app/entities/submission.model';

@Pipe({
    name: 'sortBy',
})
export class SortByPipe implements PipeTransform {
    constructor(private momentDiff: DifferencePipe) {}

    /**
     * Sorts the object according to a defined predicate.
     * @param array The array of objects to sort.
     * @param predicate The attribute of the object to consider for sorting.
     * @param reverse Whether should be sorted in reverse.
     */
    transform(array: any[], predicate: string, reverse: boolean): any[] {
        array.sort((a: any, b: any) => {
            let tempA = a;
            let tempB = b;
            if (predicate === 'status') {
                tempA['status'] = this.statusForQuiz(tempA);
                tempB['status'] = this.statusForQuiz(tempB);
            } else if (predicate === 'duration' && (!tempA['duration'] || !tempB['duration'])) {
                tempA['duration'] = this.durationForSubmission(tempA);
                tempB['duration'] = this.durationForSubmission(tempB);
            }
            const keys = predicate.split('.');
            for (const tempKey of keys) {
                if (tempA !== null) {
                    if (tempA instanceof Map) {
                        tempA = tempA.get(tempKey);
                    } else {
                        tempA = tempA[tempKey];
                    }
                }
                if (tempB !== null) {
                    if (tempB instanceof Map) {
                        tempB = tempB.get(tempKey);
                    } else {
                        tempB = tempB[tempKey];
                    }
                }
            }
            const result =
                tempA < tempB ? -1 : tempA > tempB ? 1 : tempA == null && tempB !== null ? -1 : tempA !== null && tempB == null ? 1 : a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
            return result * (reverse ? 1 : -1);
        });
        return array;
    }

    /**
     * Returns the status of the quiz represented as numbers, whether the quiz is
     * open for practice or is it visible before it starts.
     * @param quizExercise The quizExercise object.
     */
    statusForQuiz(quizExercise: any) {
        if (quizExercise.isPlannedToStart && quizExercise.remainingTime != null) {
            if (quizExercise.remainingTime <= 0) {
                return quizExercise.isOpenForPractice ? 1 : 0;
            } else {
                return 2;
            }
        }
        return quizExercise.isVisibleBeforeStart ? 3 : 4;
    }

    /**
     * Gets the duration of the submission in minutes.
     * @param exercise The exercise object.
     */
    durationForSubmission(submission: Submission) {
        return this.momentDiff.transform(submission.submissionDate!, submission.participation.initializationDate!, 'minutes');
    }
}
