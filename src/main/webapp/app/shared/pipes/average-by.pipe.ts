import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'averageBy',
})
export class AverageByPipe<T> implements PipeTransform {
    /**
     * Provides the average of an attribute. In the example below the result would be (4 + 8) / 2 = 6.
     * @param arr e.g. = [{a: 4, b: 1}, {a: 8, b: 2}]
     * @param attr e.g. = 'a'
     */
    transform(arr: T[], attr: string): number {
        return arr.reduce((acc, val) => val[attr] + acc, 0) / arr.length;
    }
}
