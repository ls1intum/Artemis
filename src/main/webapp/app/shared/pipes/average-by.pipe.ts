import { ExerciseType } from 'app/entities/exercise';
import { PipeTransform, Pipe } from '@angular/core';

@Pipe({
    name: 'averageBy',
})
export class AverageByPipe<T> implements PipeTransform {
    transform(arr: T[], attr: string): number {
        return arr.reduce((acc, val) => val[attr] + acc, 0) / arr.length;
    }
}
