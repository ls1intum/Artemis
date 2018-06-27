import { Pipe, PipeTransform } from '@angular/core';
import { DifferencePipe } from 'angular2-moment';

@Pipe({
    name: 'sortBy'
})
export class SortByPipe implements PipeTransform {
    constructor(private momentDiff: DifferencePipe) { }

    transform<T extends Id>(array: Array<T>, predicate: string, reverse: boolean): Array<T> {
        array.sort((a: T, b: T) => {
            let tempA = a;
            let tempB = b;
            if (predicate === 'releaseDate') {
                if (!tempA['isPlannedToStart']) {
                    tempA['releaseDate'] = '0';
                }
                if (!tempB['isPlannedToStart']) {
                    tempB['releaseDate'] = '0';
                }
            } else if (predicate === 'status') {
                tempA['status'] = this.statusForQuiz(tempA);
                tempB['status'] = this.statusForQuiz(tempB);
            } else if (predicate === 'duration' && (!tempA['duration'] || ! tempB['duration'])) {
                tempA['duration'] = this.momentDiff.transform(tempA['completionDate'], tempA['participation']['initializationDate'], 'minutes');
                tempB['duration'] = this.momentDiff.transform(tempB['completionDate'], tempB['participation']['initializationDate'], 'minutes');
            }
            const keys = predicate.split('.');
            for (const tempKey of keys) {
                tempA = tempA[tempKey];
                tempB = tempB[tempKey];
                if (tempA == null) {
                    break;
                }
            }
            const result = (tempA < tempB) ? -1 : (tempA > tempB) ? 1 : (
                (a.id < b.id) ? -1 : (a.id > b.id) ? 1 : 0
            );
            return result * (reverse ? 1 : -1);
        });
        return array;
    }

    statusForQuiz(quizExercise) {
        if (quizExercise['isPlannedToStart'] && quizExercise['remainingTime'] != null) {
            if (quizExercise['remainingTime'] <= 0) {
                return quizExercise['isOpenForPractice'] ? 1 : 0;
            } else {
                return 2;
            }
        }
        return quizExercise['isVisibleBeforeStart'] ? 3 : 4;
    }
}

interface Id {
    id?: number;
}
