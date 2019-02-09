import { Pipe, PipeTransform } from '@angular/core';
import { DifferencePipe } from 'angular2-moment';
import { BaseEntity } from '../../shared';

@Pipe({
    name: 'sortBy'
})
export class SortByPipe implements PipeTransform {
    constructor(private momentDiff: DifferencePipe) {}

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
                tempA < tempB
                    ? -1
                    : tempA > tempB
                    ? 1
                    : tempA == null && tempB !== null
                    ? -1
                    : tempA !== null && tempB == null
                    ? 1
                    : a.id < b.id
                    ? -1
                    : a.id > b.id
                    ? 1
                    : 0;
            return result * (reverse ? 1 : -1);
        });
        return array;
    }

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

    durationForExercise(exercise: any) {
        return this.momentDiff.transform(exercise.completionDate, exercise.participations[0].initializationDate, 'minutes');
    }
}
