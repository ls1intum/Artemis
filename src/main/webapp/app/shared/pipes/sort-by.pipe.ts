import { Pipe, PipeTransform } from '@angular/core';
import { DifferencePipe } from 'ngx-moment';

@Pipe({
    name: 'sortBy',
})
export class SortByPipe implements PipeTransform {
    constructor(private momentDiff: DifferencePipe) {}

    /**
     * Sorts the quizzes depending on their status or duration.
     * @param array The array of quiz exercises.
     * @param predicate The attribute to consider for sorting.
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
                tempA['duration'] = this.durationForExercise(tempA);
                tempB['duration'] = this.durationForExercise(tempB);
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
     * Gets the duration of the exercise in minutes.
     * @param exercise The exercise object.
     */
    durationForExercise(exercise: any) {
        // TODO: How does this work? An exercise does not have a completion date.
        return this.momentDiff.transform(exercise.completionDate, exercise.participations[0].initializationDate, 'minutes');
    }
}
